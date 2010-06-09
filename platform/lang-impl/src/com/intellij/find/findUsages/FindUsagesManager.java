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
import com.intellij.openapi.application.ReadActionProcessor;
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
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageViewManagerImpl;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
  private final List<FindUsagesHandlerFactory> myHandlers = new ArrayList<FindUsagesHandlerFactory>();

  public static class SearchData {
    public SmartPsiElementPointer[] myElements = null;
    public FindUsagesOptions myOptions = null;

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final SearchData that = (SearchData)o;

      if (!Arrays.equals(myElements, that.myElements)) return false;
      if (myOptions != null ? !myOptions.equals(that.myOptions) : that.myOptions != null) return false;

      return true;
    }

    public int hashCode() {
      return myElements != null ? Arrays.hashCode(myElements) : 0;
    }
  }

  private SearchData myLastSearchInFileData = new SearchData();
  private final CopyOnWriteArrayList<SearchData> myFindUsagesHistory = ContainerUtil.createEmptyCOWList();

  public FindUsagesManager(final Project project, com.intellij.usages.UsageViewManager anotherManager) {
    myProject = project;
    myAnotherManager = anotherManager;
  }

  public void registerFindUsagesHandler(FindUsagesHandlerFactory handler) {
    myHandlers.add(0, handler);
  }

  public boolean canFindUsages(@NotNull final PsiElement element) {
    for (FindUsagesHandlerFactory factory : myHandlers) {
      if (factory.canFindUsages(element)) {
        return true;
      }
    }
    for (FindUsagesHandlerFactory factory : Extensions.getExtensions(FindUsagesHandlerFactory.EP_NAME, myProject)) {
      if (factory.canFindUsages(element)) {
        return true;
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

  public void readExternal(Element element) throws InvalidDataException {
    myToOpenInNewTab = JDOMExternalizer.readBoolean(element, "OPEN_NEW_TAB");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizer.write(element, "OPEN_NEW_TAB", myToOpenInNewTab);
  }

  private boolean findUsageInFile(@NotNull FileEditor editor, FileSearchScope direction) {
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

    return elements.toArray(new PsiElement[elements.size()]);
  }

  private void initLastSearchElement(final FindUsagesOptions findUsagesOptions,
                                     UsageInfoToUsageConverter.TargetElementsDescriptor descriptor) {
    myLastSearchInFileData = createSearchData(descriptor.getAllElements(), findUsagesOptions);
  }

  private SearchData createSearchData(final List<? extends PsiElement> psiElements, final FindUsagesOptions findUsagesOptions) {
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
    for (FindUsagesHandlerFactory factory : myHandlers) {
      if (factory.canFindUsages(element)) {
        final FindUsagesHandler handler = factory.createFindUsagesHandler(element, forHighlightUsages);
        if (handler == FindUsagesHandler.NULL_HANDLER) return null;
        if (handler != null) {
          return handler;
        }
      }
    }
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

  public void findUsages(@NotNull PsiElement psiElement, final PsiFile scopeFile, final FileEditor editor) {
    final FindUsagesHandler handler = getFindUsagesHandler(psiElement, false);
    if (handler == null) return;

    boolean singleFile = scopeFile != null;
    final AbstractFindUsagesDialog dialog = handler.getFindUsagesDialog(singleFile, shouldOpenInNewTab(), mustOpenInNewTab());
    if (!singleFile) {
      dialog.show();
      if (!dialog.isOK()) return;
    }

    setOpenInNewTab(dialog.isShowInSeparateWindow());

    FindUsagesOptions findUsagesOptions = dialog.calcFindUsagesOptions();

    clearFindingNextUsageInFile();
    LOG.assertTrue(handler.getPsiElement().isValid());
    final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor =
      new UsageInfoToUsageConverter.TargetElementsDescriptor(handler.getPrimaryElements(), handler.getSecondaryElements());
    if (singleFile) {
      findUsagesOptions = (FindUsagesOptions)findUsagesOptions.clone();
      findUsagesOptions.isDerivedClasses = false;
      findUsagesOptions.isDerivedInterfaces = false;
      findUsagesOptions.isImplementingClasses = false;
      editor.putUserData(KEY_START_USAGE_AGAIN, null);
      findUsagesInEditor(descriptor, handler, scopeFile, FileSearchScope.FROM_START, findUsagesOptions, editor);
    }
    else {
      findUsages(descriptor, handler, dialog.isSkipResultsWhenOneUsage(), dialog.isShowInSeparateWindow(), findUsagesOptions);
    }
  }

  @Nullable
  public static SearchScope getCurrentSearchScope(FindUsagesHandler handler) {
    if (handler == null) return null;
    FindUsagesOptions findUsagesOptions = handler.getFindUsagesOptions();
    return findUsagesOptions.searchScope;
  }

  // return null on failure or cancel
  @Nullable
  public UsageViewPresentation processUsages(FindUsagesHandler handler, @NotNull final Processor<Usage> processor) {
    if (handler == null) return null;

    FindUsagesOptions findUsagesOptions = handler.getFindUsagesOptions();

    PsiElement element = handler.getPsiElement();
    LOG.assertTrue(element.isValid());
    final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor =
      new UsageInfoToUsageConverter.TargetElementsDescriptor(handler.getPrimaryElements(), handler.getSecondaryElements());

    UsageViewPresentation presentation = createPresentation(element, findUsagesOptions, myToOpenInNewTab);
    final UsageSearcher usageSearcher = createUsageSearcher(descriptor, handler, findUsagesOptions, null);
    final boolean[] canceled = {false};
    final AtomicInteger usageCount = new AtomicInteger();
    Task task = new Task.Modal(myProject, UsageViewManagerImpl.getProgressTitle(presentation), true) {
      public void run(@NotNull final ProgressIndicator indicator) {
        usageSearcher.generate(new Processor<Usage>() {
          public boolean process(final Usage usage) {
            usageCount.incrementAndGet();
            return processor.process(usage);
          }
        });
      }

      @Nullable
      public NotificationInfo getNotificationInfo() {
        return new NotificationInfo("Find Usages",  "Find Usages Finished", usageCount.get() + " Usage(s) Found");
      }

      public void onCancel() {
        canceled[0] = true;
      }
    };
    ProgressManager.getInstance().run(task);
    if (canceled[0]) return null;
    return presentation;
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


  private static UsageSearcher createUsageSearcher(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                                                   final FindUsagesHandler handler,
                                                   final FindUsagesOptions options,
                                                   final PsiFile scopeFile) {

    return new UsageSearcher() {
      public void generate(@NotNull final Processor<Usage> processor) {
        if (scopeFile != null) {
          options.searchScope = new LocalSearchScope(scopeFile);
        }
        final Processor<UsageInfo> usageInfoProcessor = new CommonProcessors.UniqueProcessor<UsageInfo>(new Processor<UsageInfo>() {
          public boolean process(UsageInfo usageInfo) {
            return processor.process(UsageInfoToUsageConverter.convert(descriptor, usageInfo));
          }
        });
        List<? extends PsiElement> elements =
          ApplicationManager.getApplication().runReadAction(new Computable<List<? extends PsiElement>>() {
            public List<? extends PsiElement> compute() {
              return descriptor.getAllElements();
            }
          });

        options.fastTrack = new SearchRequestCollector(scopeFile);

        for (final PsiElement element : elements) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              LOG.assertTrue(element.isValid());
            }
          });
          handler.processElementUsages(element, usageInfoProcessor, options);
        }

        PsiManager.getInstance(handler.getProject()).getSearchHelper().processRequests(options.fastTrack, new ReadActionProcessor<PsiReference>() {
          public boolean processInReadAction(final PsiReference ref) {
            TextRange rangeInElement = ref.getRangeInElement();
            return usageInfoProcessor.process(new UsageInfo(ref.getElement(), rangeInElement.getStartOffset(), rangeInElement.getEndOffset(), false));
          }
        });
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

  private void findUsages(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                          final FindUsagesHandler handler,
                          final boolean toSkipUsagePanelWhenOneUsage,
                          final boolean toOpenInNewTab,
                          final FindUsagesOptions findUsagesOptions) {

    List<? extends PsiElement> elements = descriptor.getAllElements();
    final UsageTarget[] targets = convertToUsageTargets(elements);
    myAnotherManager.searchAndShowUsages(targets, new Factory<UsageSearcher>() {
      public UsageSearcher create() {
        return createUsageSearcher(descriptor, handler, findUsagesOptions, null);
      }
    }, !toSkipUsagePanelWhenOneUsage, true, createPresentation(elements.get(0), findUsagesOptions, toOpenInNewTab), null);
    addToHistory(elements, findUsagesOptions);
  }

  private static UsageViewPresentation createPresentation(PsiElement psiElement,
                                                   final FindUsagesOptions findUsagesOptions,
                                                   boolean toOpenInNewTab) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    String scopeString = findUsagesOptions.searchScope != null ? findUsagesOptions.searchScope.getDisplayName() : null;
    presentation.setScopeText(scopeString);
    String usagesString = generateUsagesString(findUsagesOptions);
    presentation.setUsagesString(usagesString);
    String title;
    if (scopeString != null) {
      title = FindBundle.message("find.usages.of.element.in.scope.panel.title", usagesString, UsageViewUtil.getLongName(psiElement), scopeString);
    }
    else {
      title = FindBundle.message("find.usages.of.element.panel.title", usagesString, UsageViewUtil.getLongName(psiElement));
    }
    presentation.setTabText(title);
    presentation.setTabName(FindBundle.message("find.usages.of.element.tab.name", usagesString, UsageViewUtil.getShortName(psiElement)));
    presentation.setTargetsNodeText(StringUtil.capitalize(UsageViewUtil.getType(psiElement)));
    presentation.setOpenInNewTab(toOpenInNewTab);
    return presentation;
  }

  private void findUsagesInEditor(final UsageInfoToUsageConverter.TargetElementsDescriptor descriptor,
                                  final FindUsagesHandler handler,
                                  final PsiFile scopeFile,
                                  final FileSearchScope direction,
                                  final FindUsagesOptions findUsagesOptions,
                                  @NotNull FileEditor fileEditor) {
    initLastSearchElement(findUsagesOptions, descriptor);

    clearStatusBar();

    final FileEditorLocation currentLocation = fileEditor.getCurrentLocation();

    final UsageSearcher usageSearcher = createUsageSearcher(descriptor, handler, findUsagesOptions, scopeFile);
    final boolean[] usagesWereFound = {false};

    Usage fUsage = findSiblingUsage(myProject, usageSearcher, direction, currentLocation, usagesWereFound, fileEditor);

    if (fUsage != null) {
      fUsage.navigate(true);
      fUsage.selectInEditor();
    }
    else if (!usagesWereFound[0]) {
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
      if (shortcutsText.length() > 0) {
        message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
      }
      else {
        message = FindBundle.message("find.search.again.from.top.action.message", message);
      }
    }
    else {
      String shortcutsText =
        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS));
      if (shortcutsText.length() > 0) {
        message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
      }
      else {
        message = FindBundle.message("find.search.again.from.bottom.action.message", message);
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

  private static Usage findSiblingUsage(@NotNull final Project project,
                                        @NotNull final UsageSearcher usageSearcher,
                                 FileSearchScope dir,
                                 final FileEditorLocation currentLocation,
                                 @NotNull final boolean[] usagesWereFound,
                                 @NotNull FileEditor fileEditor) {
    if (fileEditor.getUserData(KEY_START_USAGE_AGAIN) != null) {
      dir = dir == FileSearchScope.AFTER_CARET ? FileSearchScope.FROM_START : FileSearchScope.FROM_END;
    }

    final FileSearchScope direction = dir;

    final com.intellij.usages.UsageViewManager usageViewManager = com.intellij.usages.UsageViewManager.getInstance(project);
    usageViewManager.setCurrentSearchCancelled(false);
    final Usage[] foundUsage = {null};
    usageSearcher.generate(new Processor<Usage>() {
      public boolean process(Usage usage) {
        if (usageViewManager.searchHasBeenCancelled()) return false;

        usagesWereFound[0] = true;

        if (direction == FileSearchScope.FROM_START) {
          foundUsage[0] = usage;
          return false;
        }
        else if (direction == FileSearchScope.FROM_END) {
          foundUsage[0] = usage;
        }
        else if (direction == FileSearchScope.AFTER_CARET) {
          if (Comparing.compare(usage.getLocation(), currentLocation) > 0) {
            foundUsage[0] = usage;
            return false;
          }
        }
        else if (direction == FileSearchScope.BEFORE_CARET) {
          if (Comparing.compare(usage.getLocation(), currentLocation) < 0) {
            if (foundUsage[0] != null) {
              if (foundUsage[0].getLocation().compareTo(usage.getLocation()) < 0) {
                foundUsage[0] = usage;
              }
            }
            else {
              foundUsage[0] = usage;
            }
          }
          else {
            return false;
          }
        }

        return true;
      }
    });

    fileEditor.putUserData(KEY_START_USAGE_AGAIN, null);

    return foundUsage[0];
  }

  private static void convertToUsageTarget(@NotNull List<PsiElement2UsageTargetAdapter> targets, @NotNull PsiElement elementToSearch) {
    if (elementToSearch instanceof NavigationItem) {
      targets.add(new PsiElement2UsageTargetAdapter(elementToSearch));
    }
    else {
      throw new IllegalArgumentException("Wrong usage target:" + elementToSearch+"; "+elementToSearch.getClass());
    }
  }

  private static String generateUsagesString(final FindUsagesOptions selectedOptions) {
    String suffix = " " + FindBundle.message("find.usages.panel.title.separator") + " ";
    ArrayList<String> strings = new ArrayList<String>();
    if (selectedOptions.isUsages
        || selectedOptions.isClassesUsages ||
        selectedOptions.isMethodsUsages ||
        selectedOptions.isFieldsUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    if (selectedOptions.isIncludeOverloadUsages) {
      strings.add(FindBundle.message("find.usages.panel.title.overloaded.methods.usages"));
    }
    if (selectedOptions.isDerivedClasses) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.classes"));
    }
    if (selectedOptions.isDerivedInterfaces) {
      strings.add(FindBundle.message("find.usages.panel.title.derived.interfaces"));
    }
    if (selectedOptions.isImplementingClasses) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.classes"));
    }
    if (selectedOptions.isImplementingMethods) {
      strings.add(FindBundle.message("find.usages.panel.title.implementing.methods"));
    }
    if (selectedOptions.isOverridingMethods) {
      strings.add(FindBundle.message("find.usages.panel.title.overriding.methods"));
    }
    if (strings.isEmpty()) {
      strings.add(FindBundle.message("find.usages.panel.title.usages"));
    }
    String usagesString = "";
    for (int i = 0; i < strings.size(); i++) {
      String s = strings.get(i);
      usagesString += i == strings.size() - 1 ? s : s + suffix;
    }
    return usagesString;
  }

  private static void showEditorHint(String message, final Editor editor) {
    JComponent component = HintUtil.createInformationLabel(message);
    final LightweightHint hint = new LightweightHint(component);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false);
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

  public void rerunAndRecallFromHistory(SearchData searchData) {
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

}
