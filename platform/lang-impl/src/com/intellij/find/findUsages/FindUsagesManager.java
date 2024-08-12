// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.find.findUsages.FindUsagesHandlerFactory.OperationMode;
import com.intellij.ide.util.scopeChooser.ScopeService;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.search.*;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * see {@link com.intellij.find.impl.FindManagerImpl#getFindUsagesManager()}
 */
public final class FindUsagesManager {
  private static final Logger LOG = Logger.getInstance(FindUsagesManager.class);

  private enum FileSearchScope {
    FROM_START,
    FROM_END,
    AFTER_CARET,
    BEFORE_CARET
  }

  private static final Key<String> KEY_START_USAGE_AGAIN = Key.create("KEY_START_USAGE_AGAIN");
  private static final @NonNls String VALUE_START_USAGE_AGAIN = "START_AGAIN";
  private final Project myProject;

  private LastSearchData myLastSearchInFileData; // EDT only
  private final UsageHistory myHistory = new UsageHistory();

  public FindUsagesManager(@NotNull Project project) {
    myProject = project;
  }

  public boolean canFindUsages(@NotNull PsiElement element) {
    for (FindUsagesHandlerFactory factory : FindUsagesHandlerFactory.EP_NAME.getExtensions(myProject)) {
      try {
        if (factory.canFindUsages(element)) {
          return true;
        }
      }
      catch (IndexNotReadyException | ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return false;
  }

  public void clearFindingNextUsageInFile() {
    ThreadingAssertions.assertEventDispatchThread();
    myLastSearchInFileData = null;
  }

  public boolean findNextUsageInFile(@NotNull Editor editor) {
    return findUsageInFile(editor, FileSearchScope.AFTER_CARET);
  }

  public boolean findPreviousUsageInFile(@NotNull Editor editor) {
    return findUsageInFile(editor, FileSearchScope.BEFORE_CARET);
  }

  private boolean findUsageInFile(@NotNull Editor editor, @NotNull FileSearchScope direction) {
    ThreadingAssertions.assertEventDispatchThread();

    if (myLastSearchInFileData == null) return false;
    PsiElement[] primaryElements = myLastSearchInFileData.getPrimaryElements();
    PsiElement[] secondaryElements = myLastSearchInFileData.getSecondaryElements();
    if (primaryElements.length == 0) {//all elements have been invalidated
        Messages.showMessageDialog(myProject, FindBundle.message("find.searched.elements.have.been.changed.error"),
                                   FindBundle.message("cannot.search.for.usages.title"), Messages.getInformationIcon());
        // SCR #10022
        //clearFindingNextUsageInFile();
        return false;
    }

    Document document = editor.getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null) return false;

    FindUsagesHandler handler = getFindUsagesHandler(primaryElements[0], false);
    if (handler == null) return false;
    findUsagesInEditor(primaryElements, secondaryElements, handler, psiFile, direction, myLastSearchInFileData.getOptions(), editor);
    return true;
  }


  private void initLastSearchElement(@NotNull FindUsagesOptions findUsagesOptions,
                                     PsiElement @NotNull [] primaryElements,
                                     PsiElement @NotNull [] secondaryElements) {
    ThreadingAssertions.assertEventDispatchThread();

    myLastSearchInFileData = new LastSearchData(primaryElements, secondaryElements, findUsagesOptions);
  }

  public @Nullable FindUsagesHandler getFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return getFindUsagesHandler(element, forHighlightUsages ? OperationMode.HIGHLIGHT_USAGES : OperationMode.DEFAULT);
  }

  public @Nullable FindUsagesHandler getFindUsagesHandler(@NotNull PsiElement element, @NotNull OperationMode operationMode) {
    for (FindUsagesHandlerFactory factory : FindUsagesHandlerFactory.EP_NAME.getExtensions(myProject)) {
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-353115, EA-841437")) {
        if (!factory.canFindUsages(element)) continue;
      }
      FindUsagesHandler handler = factory.createFindUsagesHandler(element, operationMode);
      if (handler == FindUsagesHandler.NULL_HANDLER) return null;
      if (handler != null) {
        return handler;
      }
    }
    return null;
  }

  public @Nullable FindUsagesHandler getNewFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    for (FindUsagesHandlerFactory factory : FindUsagesHandlerFactory.EP_NAME.getExtensionList(myProject)) {
      if (!factory.canFindUsages(element)) {
        continue;
      }

      Class<? extends FindUsagesHandlerFactory> aClass = factory.getClass();
      FindUsagesHandlerFactory copy = myProject.instantiateClass(aClass, factory.pluginDescriptor.getPluginId());
      FindUsagesHandler handler = copy.createFindUsagesHandler(element, forHighlightUsages);
      if (handler == FindUsagesHandler.NULL_HANDLER) {
        return null;
      }
      if (handler != null) {
        return handler;
      }
    }
    return null;
  }

  public void findUsages(@NotNull PsiElement psiElement, @Nullable PsiFile scopeFile, FileEditor editor, boolean showDialog, @Nullable("null means default (stored in options)") SearchScope searchScope) {
    ThreadingAssertions.assertEventDispatchThread();
    FindUsagesHandler handler = getFindUsagesHandler(psiElement, showDialog ? OperationMode.DEFAULT : OperationMode.USAGES_WITH_DEFAULT_OPTIONS);
    if (handler == null) return;

    boolean singleFile = scopeFile != null;
    AbstractFindUsagesDialog dialog = handler.getFindUsagesDialog(singleFile, shouldOpenInNewTab(), mustOpenInNewTab());
    if (showDialog) {
      if (!dialog.showAndGet()) {
        return;
      }
    }
    else {
      dialog.waitWithModalProgressUntilInitialized();
      dialog.close(DialogWrapper.OK_EXIT_CODE);
    }

    setOpenInNewTab(dialog.isShowInSeparateWindow());

    FindUsagesOptions findUsagesOptions = dialog.calcFindUsagesOptions();
    if (searchScope != null)  {
      findUsagesOptions.searchScope = searchScope;
    }

    clearFindingNextUsageInFile();

    startFindUsages(findUsagesOptions, handler, scopeFile, editor);
  }

  public void startFindUsages(@NotNull PsiElement psiElement, @NotNull FindUsagesOptions findUsagesOptions) {
    ThreadingAssertions.assertEventDispatchThread();
    FindUsagesHandler handler = getFindUsagesHandler(psiElement, false);
    if (handler == null) return;
    startFindUsages(findUsagesOptions, handler, null, null);
  }

  private void startFindUsages(@NotNull FindUsagesOptions findUsagesOptions,
                               @NotNull FindUsagesHandler handler,
                               PsiFile scopeFile,
                               FileEditor fileEditor) {
    ThreadingAssertions.assertEventDispatchThread();
    boolean singleFile = scopeFile != null;

    clearFindingNextUsageInFile();
    LOG.assertTrue(handler.getPsiElement().isValid());
    PsiElement[] primaryElements = handler.getPrimaryElements();
    checkNotNull(primaryElements, handler, "getPrimaryElements()");
    PsiElement[] secondaryElements = handler.getSecondaryElements();
    checkNotNull(secondaryElements, handler, "getSecondaryElements()");
    if (singleFile && fileEditor instanceof TextEditor) {
      Editor editor = ((TextEditor)fileEditor).getEditor();
      editor.putUserData(KEY_START_USAGE_AGAIN, null);
      findUsagesInEditor(primaryElements, secondaryElements, handler, scopeFile, FileSearchScope.FROM_START, findUsagesOptions.clone(),
                         editor);
    }
    else {
      boolean skipResultsWithOneUsage = FindSettings.getInstance().isSkipResultsWithOneUsage();
      findUsages(primaryElements, secondaryElements, handler, findUsagesOptions, skipResultsWithOneUsage);
    }
  }

  public static void showSettingsAndFindUsages(NavigationItem @NotNull [] targets) {
    if (targets.length == 0) return;
    NavigationItem target = targets[0];
    if (!(target instanceof ConfigurableUsageTarget)) return;
    ((ConfigurableUsageTarget)target).showSettings();
  }

  private static void checkNotNull(PsiElement @NotNull [] elements,
                                   @NotNull FindUsagesHandler handler,
                                   @NonNls @NotNull String methodName) {
    for (PsiElement element : elements) {
      if (element == null) {
        LOG.error(handler + "." + methodName + " has returned array with null elements: " + Arrays.asList(elements));
      }
    }
  }

  public static @NotNull ProgressIndicator startProcessUsages(@NotNull FindUsagesHandlerBase handler,
                                                              PsiElement @NotNull [] primaryElements,
                                                              PsiElement @NotNull [] secondaryElements,
                                                              @NotNull Processor<? super Usage> processor,
                                                              @NotNull FindUsagesOptions findUsagesOptions,
                                                              @NotNull Runnable onComplete) {
    ProgressIndicator indicator = new ProgressIndicatorBase();
    startProcessUsages(indicator, handler, primaryElements, secondaryElements, processor, findUsagesOptions, onComplete);
    return indicator;
  }

  private static void startProcessUsages(@NotNull ProgressIndicator indicator,
                                         @NotNull FindUsagesHandlerBase handler,
                                         PsiElement @NotNull [] primaryElements,
                                         PsiElement @NotNull [] secondaryElements,
                                         @NotNull Processor<? super Usage> processor,
                                         @NotNull FindUsagesOptions findUsagesOptions,
                                         @NotNull Runnable onComplete) {
    startProcessUsages(indicator, handler.getProject(), createUsageSearcher(handler, primaryElements, secondaryElements, findUsagesOptions), processor, onComplete);
  }

  public static UsageSearcher createUsageSearcher(@NotNull FindUsagesHandlerBase handler,
                                                  PsiElement @NotNull [] primaryElements,
                                                  PsiElement @NotNull [] secondaryElements, @NotNull FindUsagesOptions findUsagesOptions) {
    return ReadAction.compute(() -> {
      PsiElement2UsageTargetAdapter[] primaryTargets = PsiElement2UsageTargetAdapter.convert(primaryElements, false);
      PsiElement2UsageTargetAdapter[] secondaryTargets = PsiElement2UsageTargetAdapter.convert(secondaryElements, false);
      return createUsageSearcher(primaryTargets, secondaryTargets, handler, findUsagesOptions, null);
    });
  }

  public static void startProcessUsages(@NotNull ProgressIndicator indicator,
                                        final @NotNull Project project,
                                        @NotNull UsageSearcher usageSearcher,
                                        @NotNull Processor<? super Usage> processor,
                                        @NotNull Runnable onComplete) {
    ThreadingAssertions.assertEventDispatchThread();
    Task.Backgroundable task = new Task.Backgroundable(project, FindBundle.message("progress.title.finding.usages")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        usageSearcher.generate(processor);
      }
    };

    ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(task, indicator, onComplete);
  }

  public @NotNull UsageViewPresentation createPresentation(@NotNull FindUsagesHandlerBase handler, @NotNull FindUsagesOptions findUsagesOptions) {
    PsiElement element = handler.getPsiElement();
    LOG.assertTrue(element.isValid());
    return createPresentation(element, findUsagesOptions, FindSettings.getInstance().isShowResultsInSeparateView());
  }

  private void setOpenInNewTab(boolean toOpenInNewTab) {
    if (!mustOpenInNewTab()) {
      FindSettings.getInstance().setShowResultsInSeparateView(toOpenInNewTab);
    }
  }

  private boolean shouldOpenInNewTab() {
    return mustOpenInNewTab() || FindSettings.getInstance().isShowResultsInSeparateView();
  }

  private boolean mustOpenInNewTab() {
    Content selectedContent = UsageViewContentManager.getInstance(myProject).getSelectedContent(true);
    return selectedContent != null && selectedContent.isPinned();
  }


  /**
   * @throws PsiInvalidElementAccessException when the searcher can't be created (i.e. because element was invalidated)
   */
  private static @NotNull UsageSearcher createUsageSearcher(PsiElement2UsageTargetAdapter @NotNull [] primaryTargets,
                                                   PsiElement2UsageTargetAdapter @NotNull [] secondaryTargets,
                                                   @NotNull FindUsagesHandlerBase handler,
                                                   @NotNull FindUsagesOptions options,
                                                   PsiFile scopeFile) throws PsiInvalidElementAccessException {
    ReadAction.run(() -> {
      PsiElement[] primaryElements = PsiElement2UsageTargetAdapter.convertToPsiElements(primaryTargets);
      PsiElement[] secondaryElements = PsiElement2UsageTargetAdapter.convertToPsiElements(secondaryTargets);

      ContainerUtil
        .concat(primaryElements, secondaryElements)
        .forEach(psi -> {
          if (psi == null || !psi.isValid()) throw new PsiInvalidElementAccessException(psi);
        });
    });

    FindUsagesOptions optionsClone = options.clone();
    return processor -> {
      Project project = ReadAction.compute(() -> scopeFile != null ? scopeFile.getProject() : primaryTargets[0].getProject());
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

      runUpdate(primaryTargets, indicator);
      runUpdate(secondaryTargets, indicator);

      PsiElement[] primaryElements = ReadAction.compute(() -> PsiElement2UsageTargetAdapter.convertToPsiElements(primaryTargets));
      PsiElement[] secondaryElements = ReadAction.compute(() -> PsiElement2UsageTargetAdapter.convertToPsiElements(secondaryTargets));

      LOG.assertTrue(indicator != null, "Must run under progress. see ProgressManager.run*");

      ((PsiManagerImpl)PsiManager.getInstance(project)).dropResolveCacheRegularly(indicator);

      if (scopeFile != null) {
        optionsClone.searchScope = new LocalSearchScope(scopeFile);
      }
      ClusteringSearchSession clusteringSearchSession = ClusteringSearchSession.createClusteringSessionIfEnabled();
      Processor<UsageInfo> usageInfoProcessor = new CommonProcessors.UniqueProcessor<>(usageInfo -> {
        Usage usage = ReadAction.compute(
          () -> clusteringSearchSession != null
                ? UsageInfoToUsageConverter.convertToSimilarUsage(primaryElements, usageInfo, clusteringSearchSession)
                : UsageInfoToUsageConverter.convert(primaryElements, usageInfo)
        );
        return processor.process(usage);
      });
      PsiElement[] elements = ArrayUtil.mergeArrays(primaryElements, secondaryElements, PsiElement.ARRAY_FACTORY);

      optionsClone.fastTrack = new SearchRequestCollector(new SearchSession(elements));
      if (optionsClone.searchScope instanceof GlobalSearchScope) {
        // we will search in project scope always but warn if some usage is out of scope
        optionsClone.searchScope = optionsClone.searchScope.union(GlobalSearchScope.projectScope(project));
      }
      try {
        for (PsiElement element :elements) {
          if (!handler.processElementUsages(element, usageInfoProcessor, optionsClone)) {
            return;
          }

          for (CustomUsageSearcher searcher : CustomUsageSearcher.EP_NAME.getExtensionList()) {
            try {
              searcher.processElementUsages(element, processor, optionsClone);
            }
            catch (IndexNotReadyException e) {
              DumbService.getInstance(element.getProject()).showDumbModeNotificationForFunctionality(
                FindBundle.message("notification.find.usages.is.not.available.during.indexing"),
                DumbModeBlockedFunctionality.FindUsages);
            }
            catch (ProcessCanceledException e) {
              throw e;
            }
            catch (Exception e) {
              LOG.error(e);
            }
          }
        }

        PsiSearchHelper.getInstance(project)
          .processRequests(optionsClone.fastTrack, ref -> {
            UsageInfo info = ReadAction.compute(() -> {
              if (!ref.getElement().isValid()) return null;
              return new UsageInfo(ref);
            });
            return info == null || usageInfoProcessor.process(info);
          });
      }
      finally {
        optionsClone.fastTrack = null;
      }
    };
  }

  private static void runUpdate(PsiElement2UsageTargetAdapter @NotNull [] targets, @NotNull ProgressIndicator indicator) {
    for (PsiElement2UsageTargetAdapter target : targets) {
      indicator.checkCanceled();
      ReadAction.run(() -> target.update());
    }
  }

  private static PsiElement2UsageTargetAdapter @NotNull [] convertToUsageTargets(@NotNull Iterable<? extends PsiElement> elementsToSearch,
                                                                                 @NotNull FindUsagesOptions findUsagesOptions) {
    List<PsiElement2UsageTargetAdapter> targets = ContainerUtil.map(elementsToSearch,
                                                                          element -> convertToUsageTarget(element, findUsagesOptions));
    return targets.toArray(new PsiElement2UsageTargetAdapter[0]);
  }

  public void findUsages(PsiElement @NotNull [] primaryElements,
                         PsiElement @NotNull [] secondaryElements,
                         @NotNull FindUsagesHandlerBase handler,
                         @NotNull FindUsagesOptions findUsagesOptions,
                         boolean toSkipUsagePanelWhenOneUsage) {
    boolean shouldedOpenInNewTab = shouldOpenInNewTab();
    ReadAction.nonBlocking(() -> createPresentation(primaryElements[0], findUsagesOptions, shouldedOpenInNewTab))
      .expireWith(handler.getProject())
      .finishOnUiThread(ModalityState.nonModal(),
                        presentation -> doFindUsages(primaryElements, secondaryElements, handler, findUsagesOptions, toSkipUsagePanelWhenOneUsage, presentation))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  public UsageView doFindUsages(PsiElement @NotNull [] primaryElements,
                                PsiElement @NotNull [] secondaryElements,
                                @NotNull FindUsagesHandlerBase handler,
                                @NotNull FindUsagesOptions findUsagesOptions,
                                boolean toSkipUsagePanelWhenOneUsage) {
    return doFindUsages(primaryElements, secondaryElements, handler, findUsagesOptions, toSkipUsagePanelWhenOneUsage,
                        createPresentation(primaryElements[0], findUsagesOptions, shouldOpenInNewTab()));
  }

  public UsageView doFindUsages(PsiElement @NotNull [] primaryElements,
                                PsiElement @NotNull [] secondaryElements,
                                @NotNull FindUsagesHandlerBase handler,
                                @NotNull FindUsagesOptions findUsagesOptions,
                                boolean toSkipUsagePanelWhenOneUsage, UsageViewPresentation presentation) {
    if (primaryElements.length == 0) {
      throw new AssertionError(handler + " " + findUsagesOptions);
    }
    PsiElement2UsageTargetAdapter[] primaryTargets = convertToUsageTargets(Arrays.asList(primaryElements), findUsagesOptions);
    PsiElement2UsageTargetAdapter[] secondaryTargets = convertToUsageTargets(Arrays.asList(secondaryElements), findUsagesOptions);
    PsiElement2UsageTargetAdapter[] targets = ArrayUtil.mergeArrays(primaryTargets, secondaryTargets);
    Factory<UsageSearcher> factory = () -> createUsageSearcher(primaryTargets, secondaryTargets, handler, findUsagesOptions, null);
    UsageView usageView = UsageViewManager.getInstance(myProject).searchAndShowUsages(targets,
                                                                                      factory, !toSkipUsagePanelWhenOneUsage,
                                                                                      true,
                                                                                      presentation,
                                                                                      null);
    PsiElement2UsageTargetAdapter target = targets[0];
    ReadAction.nonBlocking(() -> target.getLongDescriptiveName())
      .expireWith(usageView != null ? usageView : handler.getProject())
      .finishOnUiThread(ModalityState.nonModal(), descriptiveName -> myHistory.add(target, descriptiveName))
      .submit(AppExecutorUtil.getAppExecutorService());

    return usageView;
  }

  private static @NotNull UsageViewPresentation createPresentation(@NotNull PsiElement psiElement,
                                                                   @NotNull FindUsagesOptions options,
                                                                   boolean toOpenInNewTab) {
    String usagesString = options.generateUsagesString();
    String longName = UsageViewUtil.getLongName(psiElement);
    UsageViewPresentation presentation = new UsageViewPresentation();
    String scopeString = options.searchScope.getDisplayName();
    presentation.setCodeUsagesString(options.generateUsagesString());
    presentation.setScopeText(scopeString);
    presentation.setSearchString(FindBundle.message("find.usages.of.element.tab.name", usagesString, longName));
    presentation.setTabText(FindBundle.message("find.usages.of.element.in.scope.panel.title", longName, scopeString));
    presentation.setTabName(FindBundle.message("find.usages.of.element.tab.name", usagesString, UsageViewUtil.getShortName(psiElement)));
    presentation.setTargetsNodeText(StringUtil.capitalize(UsageViewUtil.getType(psiElement)));
    presentation.setOpenInNewTab(toOpenInNewTab);
    return presentation;
  }

  private void findUsagesInEditor(PsiElement @NotNull [] primaryElements,
                                  PsiElement @NotNull [] secondaryElements,
                                  @NotNull FindUsagesHandler handler,
                                  @NotNull PsiFile scopeFile,
                                  @NotNull FileSearchScope direction,
                                  @NotNull FindUsagesOptions findUsagesOptions,
                                  @NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    initLastSearchElement(findUsagesOptions, primaryElements, secondaryElements);

    clearStatusBar();

    PsiElement2UsageTargetAdapter[] primaryTargets = PsiElement2UsageTargetAdapter.convert(primaryElements, false);
    PsiElement2UsageTargetAdapter[] secondaryTargets = PsiElement2UsageTargetAdapter.convert(primaryElements, false);
    UsageSearcher usageSearcher = createUsageSearcher(primaryTargets, secondaryTargets, handler, findUsagesOptions, scopeFile);
    AtomicBoolean usagesWereFound = new AtomicBoolean();

    int startOffset = editor.getCaretModel().getOffset();

    new Task.Backgroundable(myProject, FindBundle.message("find.progress.searching.message", "editor")){
      private Usage myUsage;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myUsage = findSiblingUsage(usageSearcher, direction, startOffset, usagesWereFound, editor);
      }

      @Override
      public void onFinished() {
        if (FindUsagesManager.this.myProject.isDisposed() || editor.isDisposed()) return;
        if (myUsage != null) {
          myUsage.navigate(true);
          myUsage.selectInEditor();
        }
        else if (!usagesWereFound.get()) {
          String message = getNoUsagesFoundMessage(primaryElements[0], scopeFile.getName());
          showEditorHint(message, editor);
        }
        else {
          editor.putUserData(KEY_START_USAGE_AGAIN, VALUE_START_USAGE_AGAIN);
          showEditorHint(getSearchAgainMessage(primaryElements[0], direction), editor);
        }
      }
    }.queue();
  }

  private static @NotNull @NlsContexts.HintText String getNoUsagesFoundMessage(@NotNull PsiElement psiElement, @NotNull String fileName) {
    String elementType = UsageViewUtil.getType(psiElement);
    String elementName = UsageViewUtil.getShortName(psiElement);
    return FindBundle.message("find.usages.of.element_type.element_name.not.found.in.scope.message", elementType, elementName, fileName);
  }

  private static @NotNull @NlsContexts.HintText String getNoUsagesFoundMessage(@NotNull PsiElement psiElement) {
    String elementType = UsageViewUtil.getType(psiElement);
    String elementName = UsageViewUtil.getShortName(psiElement);
    return FindBundle.message("find.usages.of.element_type.element_name.not.found.message", elementType, elementName);
  }

  private void clearStatusBar() {
    StatusBar.Info.set("", myProject);
  }

  private static @NotNull @NlsContexts.HintText String getSearchAgainMessage(@NotNull PsiElement element, @NotNull FileSearchScope direction) {
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

  private static Usage findSiblingUsage(@NotNull UsageSearcher usageSearcher,
                                        @NotNull FileSearchScope dir,
                                        int startOffset,
                                        @NotNull AtomicBoolean usagesWereFound,
                                        @NotNull Editor editor) {
    if (editor.getUserData(KEY_START_USAGE_AGAIN) != null) {
      dir = dir == FileSearchScope.AFTER_CARET ? FileSearchScope.FROM_START : FileSearchScope.FROM_END;
    }

    FileSearchScope direction = dir;

    AtomicReference<Usage> foundUsage = new AtomicReference<>();
    usageSearcher.generate(usage -> {
      usagesWereFound.set(true);
      int usageOffset = usage instanceof UsageInfo2UsageAdapter ? ((UsageInfo2UsageAdapter)usage).getNavigationRange().getStartOffset() : 0;
      switch (direction) {
        case FROM_START -> {
          foundUsage.compareAndSet(null, usage);
          return false;
        }
        case FROM_END -> foundUsage.set(usage);
        case AFTER_CARET -> {
          if (usageOffset > startOffset) {
            foundUsage.set(usage);
            return false;
          }
        }
        case BEFORE_CARET -> {
          if (usageOffset >= startOffset) {
            return false;
          }
          while (true) {
            Usage found = foundUsage.get();
            if (found == null) {
              if (foundUsage.compareAndSet(null, usage)) break;
            }
            else {
              int foundOffset =
                found instanceof UsageInfo2UsageAdapter ? ((UsageInfo2UsageAdapter)found).getNavigationRange().getStartOffset() : 0;
              if (foundOffset < usageOffset && foundUsage.compareAndSet(found, usage)) break;
            }
          }
        }
      }

      return true;
    });

    editor.putUserData(KEY_START_USAGE_AGAIN, null);

    return foundUsage.get();
  }

  private static @NotNull PsiElement2UsageTargetAdapter convertToUsageTarget(@NotNull PsiElement elementToSearch,
                                                                             @NotNull FindUsagesOptions findUsagesOptions) {
    if (elementToSearch instanceof NavigationItem) {
      return new PsiElement2UsageTargetAdapter(elementToSearch, findUsagesOptions, false);
    }
    throw new IllegalArgumentException("Wrong usage target:" + elementToSearch + "; " + elementToSearch.getClass());
  }

  private static void showEditorHint(@NotNull @NlsContexts.HintText String message, @NotNull Editor editor) {
    JComponent component = HintUtil.createInformationLabel(message);
    LightweightHint hint = new LightweightHint(component);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER,
                                                     HintManager.HIDE_BY_ANY_KEY |
                                                     HintManager.HIDE_BY_TEXT_CHANGE |
                                                     HintManager.HIDE_BY_SCROLLING, 0, false);
  }

  public static String getHelpID(@NotNull PsiElement element) {
    return LanguageFindUsages.getHelpId(element);
  }

  public void rerunAndRecallFromHistory(@NotNull ConfigurableUsageTarget usageTarget) {
    usageTarget.findUsages();
    addToHistory(usageTarget);
  }

  public void addToHistory(@NotNull ConfigurableUsageTarget usageTarget) {
    ReadAction.nonBlocking(() -> usageTarget.getLongDescriptiveName())
      .expireWith(myProject)
      .finishOnUiThread(ModalityState.nonModal(), descriptiveName -> myHistory.add(usageTarget, descriptiveName))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  public @NotNull UsageHistory getHistory() {
    return myHistory;
  }


  public static @NotNull GlobalSearchScope getMaximalScope(@NotNull FindUsagesHandlerBase handler) {
    PsiElement element = handler.getPsiElement();
    Project project = element.getProject();
    PsiFile file = element.getContainingFile();
    if (file != null && ProjectFileIndex.getInstance(project).isInContent(file.getViewProvider().getVirtualFile())) {
      return GlobalSearchScope.projectScope(project);
    }
    return GlobalSearchScope.allScope(project);
  }

  @TestOnly
  @RequiresEdt
  public static void waitForAsyncTaskCompletion(@NotNull Project project) {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    project.getService(ScopeService.class).waitForAsyncTaskCompletion();
  }
}