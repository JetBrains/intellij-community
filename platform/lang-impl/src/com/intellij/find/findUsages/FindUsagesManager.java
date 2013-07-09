/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.find.findUsages;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FindUsagesManager implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.findParameterUsages.FindUsagesManager");

  private enum FileSearchScope {
    FROM_START,
    FROM_END,
    AFTER_CARET,
    BEFORE_CARET
  }

  private static final Key<String> KEY_START_USAGE_AGAIN = Key.create("KEY_START_USAGE_AGAIN");
  @NonNls private static final String VALUE_START_USAGE_AGAIN = "START_AGAIN";
  private final Project myProject;
  private final com.intellij.usages.UsageViewManager myAnotherManager;
  private boolean myToOpenInNewTab = false;

  public static class SearchData {
    public SmartPsiElementPointer[] myElements = null;
    public FindUsagesOptions myOptions = null;

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final SearchData that = (SearchData)o;

      return Arrays.equals(myElements, that.myElements)
             && (myOptions != null ? myOptions.equals(that.myOptions) : that.myOptions == null);
    }

    public int hashCode() {
      return myElements != null ? Arrays.hashCode(myElements) : 0;
    }
  }

  private SearchData myLastSearchInFileData = new SearchData();
  private final List<SearchData> myFindUsagesHistory = ContainerUtil.createLockFreeCopyOnWriteList();

  public FindUsagesManager(@NotNull Project project, @NotNull com.intellij.usages.UsageViewManager anotherManager) {
    myProject = project;
    myAnotherManager = anotherManager;
  }

  public boolean canFindUsages(@NotNull final PsiElement element) {
    for (FindUsagesHandlerFactory factory : Extensions.getExtensions(FindUsagesHandlerFactory.EP_NAME, myProject)) {
      try {
        if (factory.canFindUsages(element)) {
          return true;
        }
      }
      catch (IndexNotReadyException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return false;
  }

  public void clearFindingNextUsageInFile() {
    myLastSearchInFileData.myOptions = null;
    myLastSearchInFileData.myElements = null;
  }

  public boolean findNextUsageInFile(FileEditor editor) {
    return findUsageInFile(editor, FileSearchScope.AFTER_CARET);
  }

  public boolean findPreviousUsageInFile(FileEditor editor) {
    return findUsageInFile(editor, FileSearchScope.BEFORE_CARET);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myToOpenInNewTab = JDOMExternalizer.readBoolean(element, "OPEN_NEW_TAB");
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizer.write(element, "OPEN_NEW_TAB", myToOpenInNewTab);
  }

  private boolean findUsageInFile(@NotNull FileEditor editor, @NotNull FileSearchScope direction) {
    PsiElement[] elements = restorePsiElements(myLastSearchInFileData, true);
    if (elements == null) return false;
    if (elements.length == 0) return true;//all elements have invalidated

    UsageInfoToUsageConverter.TargetElementsDescriptor descriptor = new UsageInfoToUsageConverter.TargetElementsDescriptor(elements);

    //todo
    TextEditor textEditor = (TextEditor)editor;
    Document document = textEditor.getEditor().getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null) return false;

    final FindUsagesHandler handler = getFindUsagesHandler(elements[0], false);
    if (handler == null) return false;
    findUsagesInEditor(descriptor, handler, psiFile, direction, myLastSearchInFileData.myOptions, textEditor);
    return true;
  }

  // returns null if cannot find, empty Pair if all elements have been changed
  @Nullable
  private PsiElement[] restorePsiElements(SearchData searchData, final boolean showErrorMessage) {
    if (searchData == null) return null;
    SmartPsiElementPointer[] lastSearchElements = searchData.myElements;
    if (lastSearchElements == null) return null;
    List<PsiElement> elements = new ArrayList<PsiElement>();
    for (SmartPsiElementPointer pointer : lastSearchElements) {
      PsiElement element = pointer.getElement();
      if (element != null) elements.add(element);
    }
    if (elements.isEmpty() && showErrorMessage) {
      Messages.showMessageDialog(myProject, FindBundle.message("find.searched.elements.have.been.changed.error"),
                                 FindBundle.message("cannot.search.for.usages.title"), Messages.getInformationIcon());
      // SCR #10022
      //clearFindingNextUsageInFile();
      return PsiElement.EMPTY_ARRAY;
    }

    return PsiUtilCore.toPsiElementArray(elements);
  }

  private void initLastSearchElement(final FindUsagesOptions findUsagesOptions,
                                     UsageInfoToUsageConverter.TargetElementsDescriptor descriptor) {
    myLastSearchInFileData = createSearchData(descriptor.getAllElements(), findUsagesOptions);
  }

  private SearchData createSearchData(@NotNull List<? extends PsiElement> psiElements, final FindUsagesOptions findUsagesOptions) {
    SearchData data = new SearchData();

    data.myElements = new SmartPsiElementPointer[psiElements.size()];
    int idx = 0;
    for (PsiElement psiElement : psiElements) {
      data.myElements[idx++] = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(psiElement);
    }
    data.myOptions = findUsagesOptions;
    return data;
  }

  @Nullable
  public FindUsagesHandler getFindUsagesHandler(PsiElement element, final boolean forHighlightUsages) {
    for (FindUsagesHandlerFactory factory : Extensions.getExtensions(FindUsagesHandlerFactory.EP_NAME, myProject)) {
      if (factory.canFindUsages(element)) {
        final FindUsagesHandler handler = factory.createFindUsagesHandler(element, forHighlightUsages);
        if (handler == FindUsagesHandler.NULL_HANDLER) return null;
        if (handler != null) {
          return handler;
        }
      }
    }
    return null;
  }

  @Nullable
  public FindUsagesHandler getNewFindUsagesHandler(@NotNull PsiElement element, final boolean forHighlightUsages) {
    for (FindUsagesHandlerFactory factory : Extensions.getExtensions(FindUsagesHandlerFactory.EP_NAME, myProject)) {
      if (factory.canFindUsages(element)) {
        Class<? extends FindUsagesHandlerFactory> aClass = factory.getClass();
        FindUsagesHandlerFactory copy = (FindUsagesHandlerFactory)new ConstructorInjectionComponentAdapter(aClass.getName(), aClass)
          .getComponentInstance(myProject.getPicoContainer());
        final FindUsagesHandler handler = copy.createFindUsagesHandler(element, forHighlightUsages);
        if (handler == FindUsagesHandler.NULL_HANDLER) return null;
        if (handler != null) {
          return handler;
        }
      }
    }
    return null;
  }

  public void findUsages(@NotNull PsiElement psiElement, final PsiFile scopeFile, final FileEditor editor, boolean showDialog) {
    doShowDialogAndStartFind(psiElement, scopeFile, editor, showDialog, true);
  }

  private void doShowDialogAndStartFind(@NotNull PsiElement psiElement,
                                        PsiFile scopeFile,
                                        FileEditor editor,
                                        boolean showDialog,
                                        boolean useMaximalScope) {
    FindUsagesHandler handler = getNewFindUsagesHandler(psiElement, false);
    if (handler == null) return;

    boolean singleFile = scopeFile != null;
    AbstractFindUsagesDialog dialog = handler.getFindUsagesDialog(singleFile, shouldOpenInNewTab(), mustOpenInNewTab());
    if (showDialog) {
      dialog.show();
      if (!dialog.isOK()) return;
    }
    else {
      dialog.close(DialogWrapper.OK_EXIT_CODE);
    }

    setOpenInNewTab(dialog.isShowInSeparateWindow());

    FindUsagesOptions findUsagesOptions = dialog.calcFindUsagesOptions();
    if (!showDialog && useMaximalScope) {
      findUsagesOptions.searchScope = getMaximalScope(handler);
    }

    clearFindingNextUsageInFile();
    LOG.assertTrue(handler.getPsiElement().isValid());
    PsiElement[] primaryElements = handler.getPrimaryElements();
    checkNotNull(primaryElements, handler, "getPrimaryElements()");
    PsiElement[] secondaryElements = handler.getSecondaryElements();
    checkNotNull(secondaryElements, handler, "getSecondaryElements()");
    UsageInfoToUsageConverter.TargetElementsDescriptor descriptor =
      new UsageInfoToUsageConverter.TargetElementsDescriptor(primaryElements, secondaryElements);
    if (singleFile) {
      findUsagesOptions = findUsagesOptions.clone();
      editor.putUserData(KEY_START_USAGE_AGAIN, null);
      findUsagesInEditor(descriptor, handler, scopeFile, FileSearchScope.FROM_START, findUsagesOptions, editor);
    }
    else {
      findUsages(descriptor, handler, dialog.isSkipResultsWhenOneUsage(), dialog.isShowInSeparateWindow(), findUsagesOptions);
    }
  }

  public void showSettingsAndFindUsages(@NotNull NavigationItem[] targets) {
    UsageTarget[] usageTargets = (UsageTarget[])targets;
    PsiElement[] elements = getPsiElements(usageTargets);
    if (elements.length == 0) return;
    PsiElement psiElement = elements[0];
    doShowDialogAndStartFind(psiElement, null, null, true, false);
  }

  private static void checkNotNull(@NotNull PsiElement[] primaryElements,
                                   @NotNull FindUsagesHandler handler,
                                   @NonNls @NotNull String methodName) {
    for (PsiElement element : primaryElements) {
      if (element == null) {
        LOG.error(handler + "." + methodName + " has returned array with null elements: " + Arrays.asList(primaryElements));
      }
    }
  }

  public boolean isUsed(@NotNull PsiElement element, @NotNull FindUsagesOptions findUsagesOptions) {
    FindUsagesHandler handler = getFindUsagesHandler(element, true);
    if (handler == null) return false;
    UsageInfoToUsageConverter.TargetElementsDescriptor descriptor = new UsageInfoToUsageConverter.TargetElementsDescriptor(element);
    UsageSearcher usageSearcher = createUsageSearcher(descriptor, handler, findUsagesOptions, null);
    final AtomicBoolean used = new AtomicBoolean();
    usageSearcher.generate(new Processor<Usage>() {
      @Override
      public boolean process(final Usage usage) {
        if (isInComment(usage)) return true;
        used.set(true);
        return false;
      }
    });
    return used.get();
  }

  private static boolean isInComment(Usage usage) {
    if (!(usage instanceof UsageInfo2UsageAdapter)) return false;
    UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();
    if (!usageInfo.isNonCodeUsage()) return false;
    SmartPsiFileRange psiRangePointer = usageInfo.getPsiFileRange();
    if (psiRangePointer == null) return false;
    Segment range = psiRangePointer.getRange();
    PsiFile file = psiRangePointer.getContainingFile();
    if (file == null || range == null) return false;
    PsiElement element = file.findElementAt(range.getStartOffset());
    return element instanceof PsiComment;
  }

  @NotNull
  public static ProgressIndicator startProcessUsages(@NotNull FindUsagesHandler handler,
                                                     @NotNull UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                                                     @NotNull final Processor<Usage> processor,
                                                     @NotNull FindUsagesOptions findUsagesOptions,
                                                     @NotNull final Runnable onComplete) {
    final UsageSearcher usageSearcher = createUsageSearcher(descriptor, handler, findUsagesOptions, null);

    final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    dropResolveCacheRegularly(indicator, handler.getProject());
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          ProgressManager.getInstance().runProcess(new Runnable() {
            @Override
            public void run() {
              usageSearcher.generate(processor);
            }
          }, indicator);
        }
        finally {
          onComplete.run();
        }
      }
    });

    return indicator;
  }

  @NotNull
  public UsageViewPresentation createPresentation(@NotNull FindUsagesHandler handler, @NotNull FindUsagesOptions findUsagesOptions) {
    PsiElement element = handler.getPsiElement();
    LOG.assertTrue(element.isValid());
    return createPresentation(element, findUsagesOptions, myToOpenInNewTab);
  }

  private void setOpenInNewTab(final boolean toOpenInNewTab) {
    if (!mustOpenInNewTab()) {
      myToOpenInNewTab = toOpenInNewTab;
    }
  }

  private boolean shouldOpenInNewTab() {
    return mustOpenInNewTab() || myToOpenInNewTab;
  }

  private boolean mustOpenInNewTab() {
    Content selectedContent = UsageViewManager.getInstance(myProject).getSelectedContent(true);
    return selectedContent != null && selectedContent.isPinned();
  }


  private static UsageSearcher createUsageSearcher(@NotNull final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                                                   @NotNull final FindUsagesHandler handler,
                                                   @NotNull FindUsagesOptions _options,
                                                   final PsiFile scopeFile) {
    final FindUsagesOptions options = _options.clone();
    return new UsageSearcher() {
      @Override
      public void generate(@NotNull final Processor<Usage> processor) {
        if (scopeFile != null) {
          options.searchScope = new LocalSearchScope(scopeFile);
        }
        final Processor<UsageInfo> usageInfoProcessor = new CommonProcessors.UniqueProcessor<UsageInfo>(new Processor<UsageInfo>() {
          @Override
          public boolean process(final UsageInfo usageInfo) {
            Usage usage = ApplicationManager.getApplication().runReadAction(new Computable<Usage>() {
              @Override
              public Usage compute() {
                return UsageInfoToUsageConverter.convert(descriptor, usageInfo);
              }
            });
            return processor.process(usage);
          }
        });
        final List<? extends PsiElement> elements =
          ApplicationManager.getApplication().runReadAction(new Computable<List<? extends PsiElement>>() {
            @Override
            public List<? extends PsiElement> compute() {
              return descriptor.getAllElements();
            }
          });

        options.fastTrack = new SearchRequestCollector(new SearchSession());

        try {
          for (final PsiElement element : elements) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
                LOG.assertTrue(element.isValid());
              }
            });
            handler.processElementUsages(element, usageInfoProcessor, options);
            for (CustomUsageSearcher searcher : Extensions.getExtensions(CustomUsageSearcher.EP_NAME)) {
              try {
                searcher.processElementUsages(element, processor, options);
              }
              catch (IndexNotReadyException e) {
                DumbService.getInstance(element.getProject()).showDumbModeNotification("Find usages is not available during indexing");
              }
              catch (Exception e) {
                LOG.error(e);
              }
            }
          }

          Project project = ApplicationManager.getApplication().runReadAction(new Computable<Project>() {
            @Override
            public Project compute() {
              return scopeFile != null ? scopeFile.getProject() : !elements.isEmpty() ? elements.get(0).getProject() : handler.getProject();
            }
          });
          PsiSearchHelper.SERVICE.getInstance(project)
            .processRequests(options.fastTrack, new Processor<PsiReference>() {
              @Override
              public boolean process(final PsiReference ref) {
                UsageInfo info = ApplicationManager.getApplication().runReadAction(new Computable<UsageInfo>() {
                  @Override
                  public UsageInfo compute() {
                    if (!ref.getElement().isValid()) return null;
                    return new UsageInfo(ref);
                  }
                });
                return info == null || usageInfoProcessor.process(info);
              }
            });
        }
        finally {
          options.fastTrack = null;
        }
      }
    };
  }


  private static PsiElement2UsageTargetAdapter[] convertToUsageTargets(final List<? extends PsiElement> elementsToSearch) {
    final ArrayList<PsiElement2UsageTargetAdapter> targets = new ArrayList<PsiElement2UsageTargetAdapter>(elementsToSearch.size());
    for (PsiElement element : elementsToSearch) {
      convertToUsageTarget(targets, element);
    }
    return targets.toArray(new PsiElement2UsageTargetAdapter[targets.size()]);
  }

  private void findUsages(@NotNull final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                          @NotNull final FindUsagesHandler handler,
                          final boolean toSkipUsagePanelWhenOneUsage,
                          final boolean toOpenInNewTab,
                          @NotNull final FindUsagesOptions findUsagesOptions) {
    List<? extends PsiElement> elements = descriptor.getAllElements();
    if (elements.isEmpty()) {
      throw new AssertionError(handler + " " + findUsagesOptions);
    }
    final UsageTarget[] targets = convertToUsageTargets(elements);
    myAnotherManager.searchAndShowUsages(targets, new Factory<UsageSearcher>() {
      @Override
      public UsageSearcher create() {
        dropResolveCacheRegularly(ProgressManager.getInstance().getProgressIndicator(), myProject);
        return createUsageSearcher(descriptor, handler, findUsagesOptions, null);
      }
    }, !toSkipUsagePanelWhenOneUsage, true, createPresentation(elements.get(0), findUsagesOptions, toOpenInNewTab), null);
    addToHistory(elements, findUsagesOptions);
  }

  private static void dropResolveCacheRegularly(ProgressIndicator indicator, final Project project) {
    if (indicator instanceof ProgressIndicatorEx) {
      ((ProgressIndicatorEx)indicator).addStateDelegate(new ProgressIndicatorBase() {
        volatile long lastCleared = System.currentTimeMillis();

        @Override
        public void setFraction(double fraction) {
          super.setFraction(fraction);
          long current = System.currentTimeMillis();
          if (current - lastCleared >= 500) {
            lastCleared = current;
            // fraction is changed when each file is processed =>
            // resolve caches used when searching in that file are likely to be not needed anymore
            PsiManager.getInstance(project).dropResolveCaches();
          }
        }
      });
    }
  }

  @NotNull
  private static UsageViewPresentation createPresentation(@NotNull PsiElement psiElement,
                                                          @NotNull FindUsagesOptions findUsagesOptions,
                                                          boolean toOpenInNewTab) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    String scopeString = findUsagesOptions.searchScope == null ? null : findUsagesOptions.searchScope.getDisplayName();
    presentation.setScopeText(scopeString);
    String usagesString = generateUsagesString(findUsagesOptions);
    presentation.setUsagesString(usagesString);
    String title = scopeString == null
                   ? FindBundle.message("find.usages.of.element.panel.title", usagesString, UsageViewUtil.getLongName(psiElement))
                   : FindBundle.message("find.usages.of.element.in.scope.panel.title", usagesString, UsageViewUtil.getLongName(psiElement),
                                        scopeString);
    presentation.setTabText(title);
    presentation.setTabName(FindBundle.message("find.usages.of.element.tab.name", usagesString, UsageViewUtil.getShortName(psiElement)));
    presentation.setTargetsNodeText(StringUtil.capitalize(UsageViewUtil.getType(psiElement)));
    presentation.setOpenInNewTab(toOpenInNewTab);
    return presentation;
  }

  private void findUsagesInEditor(@NotNull UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                                  @NotNull FindUsagesHandler handler,
                                  @NotNull PsiFile scopeFile,
                                  @NotNull FileSearchScope direction,
                                  @NotNull final FindUsagesOptions findUsagesOptions,
                                  @NotNull FileEditor fileEditor) {
    initLastSearchElement(findUsagesOptions, descriptor);

    clearStatusBar();

    final FileEditorLocation currentLocation = fileEditor.getCurrentLocation();

    final UsageSearcher usageSearcher = createUsageSearcher(descriptor, handler, findUsagesOptions, scopeFile);
    AtomicBoolean usagesWereFound = new AtomicBoolean();

    Usage fUsage = findSiblingUsage(usageSearcher, direction, currentLocation, usagesWereFound, fileEditor);

    if (fUsage != null) {
      fUsage.navigate(true);
      fUsage.selectInEditor();
    }
    else if (!usagesWereFound.get()) {
      String message = getNoUsagesFoundMessage(descriptor.getPrimaryElements()[0]) + " in " + scopeFile.getName();
      showHintOrStatusBarMessage(message, fileEditor);
    }
    else {
      fileEditor.putUserData(KEY_START_USAGE_AGAIN, VALUE_START_USAGE_AGAIN);
      showHintOrStatusBarMessage(getSearchAgainMessage(descriptor.getPrimaryElements()[0], direction), fileEditor);
    }
  }

  private static String getNoUsagesFoundMessage(PsiElement psiElement) {
    String elementType = UsageViewUtil.getType(psiElement);
    String elementName = UsageViewUtil.getShortName(psiElement);
    return FindBundle.message("find.usages.of.element_type.element_name.not.found.message", elementType, elementName);
  }

  private void clearStatusBar() {
    StatusBar.Info.set("", myProject);
  }

  private static String getSearchAgainMessage(PsiElement element, final FileSearchScope direction) {
    String message = getNoUsagesFoundMessage(element);
    if (direction == FileSearchScope.AFTER_CARET) {
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT);
      String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
      if (shortcutsText.isEmpty()) {
        message = FindBundle.message("find.search.again.from.top.action.message", message);
      }
      else {
        message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
      }
    }
    else {
      String shortcutsText =
        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS));
      if (shortcutsText.isEmpty()) {
        message = FindBundle.message("find.search.again.from.bottom.action.message", message);
      }
      else {
        message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
      }
    }
    return message;
  }

  private void showHintOrStatusBarMessage(String message, FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      showEditorHint(message, textEditor.getEditor());
    }
    else {
      StatusBar.Info.set(message, myProject);
    }
  }

  private static Usage findSiblingUsage(@NotNull final UsageSearcher usageSearcher,
                                        @NotNull FileSearchScope dir,
                                        final FileEditorLocation currentLocation,
                                        @NotNull final AtomicBoolean usagesWereFound,
                                        @NotNull FileEditor fileEditor) {
    if (fileEditor.getUserData(KEY_START_USAGE_AGAIN) != null) {
      dir = dir == FileSearchScope.AFTER_CARET ? FileSearchScope.FROM_START : FileSearchScope.FROM_END;
    }

    final FileSearchScope direction = dir;

    final AtomicReference<Usage> foundUsage = new AtomicReference<Usage>();
    usageSearcher.generate(new Processor<Usage>() {
      @Override
      public boolean process(Usage usage) {
        usagesWereFound.set(true);
        if (direction == FileSearchScope.FROM_START) {
          foundUsage.compareAndSet(null, usage);
          return false;
        }
        if (direction == FileSearchScope.FROM_END) {
          foundUsage.set(usage);
        }
        else if (direction == FileSearchScope.AFTER_CARET) {
          if (Comparing.compare(usage.getLocation(), currentLocation) > 0) {
            foundUsage.set(usage);
            return false;
          }
        }
        else if (direction == FileSearchScope.BEFORE_CARET) {
          if (Comparing.compare(usage.getLocation(), currentLocation) >= 0) {
            return false;
          }
          while (true) {
            Usage found = foundUsage.get();
            if (found == null) {
              if (foundUsage.compareAndSet(null, usage)) break;
            }
            else {
              if (Comparing.compare(found.getLocation(), usage.getLocation()) < 0 && foundUsage.compareAndSet(found, usage)) break;
            }
          }
        }

        return true;
      }
    });

    fileEditor.putUserData(KEY_START_USAGE_AGAIN, null);

    return foundUsage.get();
  }

  private static void convertToUsageTarget(@NotNull List<PsiElement2UsageTargetAdapter> targets, @NotNull PsiElement elementToSearch) {
    if (elementToSearch instanceof NavigationItem) {
      targets.add(new PsiElement2UsageTargetAdapter(elementToSearch));
    }
    else {
      throw new IllegalArgumentException("Wrong usage target:" + elementToSearch + "; " + elementToSearch.getClass());
    }
  }

  private static String generateUsagesString(final FindUsagesOptions selectedOptions) {
    return selectedOptions.generateUsagesString();
  }

  private static void showEditorHint(String message, final Editor editor) {
    JComponent component = HintUtil.createInformationLabel(message);
    final LightweightHint hint = new LightweightHint(component);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER,
                                                     HintManager.HIDE_BY_ANY_KEY |
                                                     HintManager.HIDE_BY_TEXT_CHANGE |
                                                     HintManager.HIDE_BY_SCROLLING, 0, false);
  }

  public static String getHelpID(PsiElement element) {
    return LanguageFindUsages.INSTANCE.forLanguage(element.getLanguage()).getHelpId(element);
  }

  private void addToHistory(final List<? extends PsiElement> elements, final FindUsagesOptions findUsagesOptions) {
    SearchData data = createSearchData(elements, findUsagesOptions);
    myFindUsagesHistory.remove(data);
    myFindUsagesHistory.add(data);

    // todo configure history depth limit
    if (myFindUsagesHistory.size() > 15) {
      myFindUsagesHistory.remove(0);
    }
  }

  public void rerunAndRecallFromHistory(@NotNull SearchData searchData) {
    myFindUsagesHistory.remove(searchData);
    PsiElement[] elements = restorePsiElements(searchData, true);
    if (elements == null || elements.length == 0) return;
    UsageInfoToUsageConverter.TargetElementsDescriptor descriptor = new UsageInfoToUsageConverter.TargetElementsDescriptor(elements);
    final FindUsagesHandler handler = getFindUsagesHandler(elements[0], false);
    if (handler == null) return;
    findUsages(descriptor, handler, false, false, searchData.myOptions);
  }

  // most recent entry is at the end of the list
  public List<SearchData> getFindUsageHistory() {
    removeInvalidElementsFromHistory();
    return Collections.unmodifiableList(myFindUsagesHistory);
  }

  private void removeInvalidElementsFromHistory() {
    for (SearchData data : myFindUsagesHistory) {
      PsiElement[] elements = restorePsiElements(data, false);
      if (elements == null || elements.length == 0) myFindUsagesHistory.remove(data);
    }
  }


  @NotNull
  private static PsiElement[] getPsiElements(@NotNull UsageTarget[] targets) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    for (UsageTarget target : targets) {
      if (target instanceof PsiElementUsageTarget) {
        PsiElement element = ((PsiElementUsageTarget)target).getElement();
        if (element != null) {
          result.add(element);
        }
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  @NotNull
  public static GlobalSearchScope getMaximalScope(@NotNull FindUsagesHandler handler) {
    PsiElement element = handler.getPsiElement();
    Project project = element.getProject();
    PsiFile file = element.getContainingFile();
    if (file != null && ProjectFileIndex.SERVICE.getInstance(project).isInContent(file.getViewProvider().getVirtualFile())) {
      return GlobalSearchScope.projectScope(project);
    }
    return GlobalSearchScope.allScope(project);
  }
}
