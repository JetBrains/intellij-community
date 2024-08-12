// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.replaceInProject;

import com.intellij.find.*;
import com.intellij.find.actions.FindInPathAction;
import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DataManager;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.AdapterProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

public class ReplaceInProjectManager {
  private static final NotificationGroup NOTIFICATION_GROUP = FindInPathAction.NOTIFICATION_GROUP;

  private final Project myProject;
  private boolean myIsFindInProgress;

  public static ReplaceInProjectManager getInstance(Project project) {
    return project.getService(ReplaceInProjectManager.class);
  }

  public ReplaceInProjectManager(Project project) {
    myProject = project;
  }

  static final class ReplaceContext {
    private final UsageView usageView;
    private final FindModel findModel;
    private Set<Usage> excludedSet;

    ReplaceContext(@NotNull UsageView usageView, @NotNull FindModel findModel) {
      this.usageView = usageView;
      this.findModel = findModel;
    }

    public @NotNull FindModel getFindModel() {
      return findModel;
    }

    public @NotNull UsageView getUsageView() {
      return usageView;
    }

    @NotNull
    Set<Usage> getExcludedSetCached() {
      if (excludedSet == null) excludedSet = usageView.getExcludedUsages();
      return excludedSet;
    }

    void invalidateExcludedSetCache() {
      excludedSet = null;
    }
  }

  /**
   * @param model would be used for replacing if not null, otherwise shared (project-level) model would be used
   */
  public void replaceInProject(@NotNull DataContext dataContext, @Nullable FindModel model) {
    final FindManager findManager = FindManager.getInstance(myProject);
    final FindModel findModel;

    final boolean isOpenInNewTabEnabled;
    final boolean toOpenInNewTab;
    final Content selectedContent = UsageViewContentManager.getInstance(myProject).getSelectedContent(true);
    if (selectedContent != null && selectedContent.isPinned()) {
      toOpenInNewTab = true;
      isOpenInNewTabEnabled = false;
    }
    else {
      toOpenInNewTab = FindSettings.getInstance().isShowResultsInSeparateView();
      isOpenInNewTabEnabled = UsageViewContentManager.getInstance(myProject).getReusableContentsCount() > 0;
    }
    if (model == null) {
      findModel = findManager.getFindInProjectModel().clone();
      findModel.setReplaceState(true);
      findModel.setOpenInNewTabEnabled(isOpenInNewTabEnabled);
      findModel.setOpenInNewTab(toOpenInNewTab);
      initModel(findModel, dataContext);
    }
    else {
      findModel = model;
      findModel.setOpenInNewTabEnabled(isOpenInNewTabEnabled);
    }

    findManager.showFindDialog(findModel, () -> {
      if (findModel.isReplaceState()) {
        replaceInPath(findModel);
      } else {
        FindInProjectManager.getInstance(myProject).findInPath(findModel);
      }
    });
  }

  protected void initModel(@NotNull FindModel findModel, @NotNull DataContext dataContext) {
    FindInProjectUtil.setScope(myProject, findModel, dataContext);
    FindInProjectUtil.initStringToFindFromDataContext(findModel, dataContext);
  }

  public void replaceInPath(@NotNull FindModel findModel) {
    FindManager findManager = FindManager.getInstance(myProject);
    if (!findModel.isProjectScope() &&
        FindInProjectUtil.getDirectory(findModel) == null &&
        findModel.getModuleName() == null &&
        findModel.getCustomScope() == null) {
      return;
    }

    UsageViewManager manager = UsageViewManager.getInstance(myProject);

    if (manager == null) return;
    findManager.getFindInProjectModel().copyFrom(findModel);
    final FindModel findModelCopy = findModel.clone();

    final UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(findModelCopy);
    final FindUsagesProcessPresentation processPresentation = FindInProjectUtil.setupProcessPresentation(true, presentation);
    processPresentation.setShowFindOptionsPrompt(findModel.isPromptOnReplace());

    UsageSearcherFactory factory = new UsageSearcherFactory(findModelCopy, processPresentation);
    searchAndShowUsages(manager, factory, findModelCopy, presentation, processPresentation);
  }

  private static final class ReplaceInProjectTarget extends FindInProjectUtil.StringUsageTarget {
    ReplaceInProjectTarget(@NotNull Project project, @NotNull FindModel findModel) {
      super(project, findModel);
    }

    @Override
    public @Nls @NotNull String getLongDescriptiveName() {
      UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(myFindModel);
      return StringUtil.decapitalize(presentation.getToolwindowTitle());
    }

    @Override
    public KeyboardShortcut getShortcut() {
      return ActionManager.getInstance().getKeyboardShortcut(IdeActions.ACTION_REPLACE_IN_PATH);
    }

    @Override
    public void showSettings() {
      Content selectedContent = UsageViewContentManager.getInstance(myProject).getSelectedContent(true);
      JComponent component = selectedContent == null ? null : selectedContent.getComponent();
      ReplaceInProjectManager findInProjectManager = getInstance(myProject);
      findInProjectManager.replaceInProject(DataManager.getInstance().getDataContext(component), myFindModel);
    }
  }

  public void searchAndShowUsages(@NotNull UsageViewManager manager,
                                  @NotNull Factory<? extends UsageSearcher> usageSearcherFactory,
                                  final @NotNull FindModel findModelCopy,
                                  @NotNull UsageViewPresentation presentation,
                                  @NotNull FindUsagesProcessPresentation processPresentation) {
    presentation.setMergeDupLinesAvailable(false);
    final ReplaceInProjectTarget target = new ReplaceInProjectTarget(myProject, findModelCopy);
    ((FindManagerImpl)FindManager.getInstance(myProject)).getFindUsagesManager().addToHistory(target);
    final ReplaceContext[] context = new ReplaceContext[1];
    manager.searchAndShowUsages(new UsageTarget[]{target},
                                usageSearcherFactory, processPresentation, presentation, new UsageViewManager.UsageViewStateListener() {
        @Override
        public void usageViewCreated(@NotNull UsageView usageView) {
          context[0] = new ReplaceContext(usageView, findModelCopy);
          addReplaceActions(context[0]);
          usageView.setRerunAction(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
              UsageViewPresentation rerunPresentation = presentation.copy();
              rerunPresentation.setOpenInNewTab(false);
              searchAndShowUsages(manager, usageSearcherFactory, findModelCopy, rerunPresentation, processPresentation);
            }
          });
        }

        @Override
        public void findingUsagesFinished(final UsageView usageView) {
          if (context[0] != null && !processPresentation.isShowFindOptionsPrompt()) {
            ApplicationManager.getApplication().invokeLater(() -> {
              replaceUsagesUnderCommand(context[0], usageView.getUsages(), true);
            }, myProject.getDisposed());
          }
        }
      });
  }

  public boolean showReplaceAllConfirmDialog(@NotNull String usagesCount, @NotNull String stringToFind, @NotNull String filesCount, @NotNull String stringToReplace) {
    String message = stringToFind.length() < 400 && stringToReplace.length() < 400
                     ? FindBundle.message("find.replace.all.confirmation", usagesCount,
                                          StringUtil.escapeXmlEntities(stringToFind),
                                          filesCount,
                                          StringUtil.escapeXmlEntities(stringToReplace))
                     : FindBundle.message("find.replace.all.confirmation.long.text", usagesCount,
                                          StringUtil.trimMiddle(StringUtil.escapeXmlEntities(stringToFind), 400),
                                          filesCount,
                                          StringUtil.trimMiddle(StringUtil.escapeXmlEntities(stringToReplace), 400));
    return MessageDialogBuilder.yesNo(FindBundle.message("find.replace.all.confirmation.title"), message)
      .yesText(FindBundle.message("find.replace.command"))
      .noText(Messages.getCancelButton())
      .ask(myProject);
  }

  private static Set<VirtualFile> getFiles(@NotNull Collection<Usage> usages) {
    return ContainerUtil.map2Set(usages, usage -> ((UsageInfo2UsageAdapter)usage).getFile());
  }

  private void addReplaceActions(final ReplaceContext replaceContext) {
    final AbstractAction replaceAllAction = new AbstractAction(FindBundle.message("find.replace.all.action")) {
      {
        KeyStroke altShiftEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
        putValue(ACCELERATOR_KEY, altShiftEnter);
        putValue(SHORT_DESCRIPTION, KeymapUtil.getKeystrokeText(altShiftEnter));
      }
      @Override
      public void actionPerformed(ActionEvent e) {
        UsageView usageView = replaceContext.getUsageView();
        Set<Usage> usages = new HashSet<>(usageView.getUsages());
        usages.removeAll(usageView.getExcludedUsages());
        if (usages.isEmpty()) return;
        Set<VirtualFile> files = getFiles(usages);
        if (files.size() < 2 || showReplaceAllConfirmDialog(
          String.valueOf(usages.size()),
          replaceContext.getFindModel().getStringToFind(),
          String.valueOf(files.size()),
          replaceContext.getFindModel().getStringToReplace())) {
          replaceUsagesUnderCommand(replaceContext, usages, true);
        }
      }

      @Override
      public boolean isEnabled() {
        return !replaceContext.getUsageView().getUsages().isEmpty();
      }
    };
    replaceContext.getUsageView().addButtonToLowerPane(replaceAllAction);

    final AbstractAction replaceSelectedAction = new AbstractAction() {
      {
        KeyStroke altEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK);
        putValue(ACCELERATOR_KEY, altEnter);
        putValue(LONG_DESCRIPTION, KeymapUtil.getKeystrokeText(altEnter));
        putValue(SHORT_DESCRIPTION, KeymapUtil.getKeystrokeText(altEnter));
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        replaceUsagesUnderCommand(replaceContext, getSelectedUsages(), false);
      }

      @Override
      public Object getValue(String key) {
        return Action.NAME.equals(key)
               ? FindBundle.message("find.replace.selected.action", getSelectedUsages().size())
               : super.getValue(key);
      }

      @Override
      public boolean isEnabled() {
        return !getSelectedUsages().isEmpty();
      }

      private Set<Usage> getSelectedUsages() {
        UsageView usageView = replaceContext.getUsageView();
        Set<Usage> selectedUsages = usageView.getSelectedUsages();
        selectedUsages.removeAll(usageView.getExcludedUsages());
        return selectedUsages;
      }
    };

    replaceContext.getUsageView().addButtonToLowerPane(replaceSelectedAction);
  }

  private boolean replaceUsages(@NotNull ReplaceContext replaceContext, @NotNull Collection<? extends Usage> usages) {
    int[] replacedCount = {0};
    boolean[] success = {true};
    boolean result = ((ApplicationImpl)ApplicationManager.getApplication()).runWriteActionWithCancellableProgressInDispatchThread(
      FindBundle.message("find.replace.all.progress.title"),
      myProject,
      null,
      indicator -> {
        indicator.setIndeterminate(false);
        int processed = 0;
        VirtualFile lastFile = null;

        for (final Usage usage : usages) {
          ++processed;
          indicator.checkCanceled();
          indicator.setFraction((float)processed / usages.size());

          if (usage instanceof UsageInFile) {
            VirtualFile virtualFile = ((UsageInFile)usage).getFile();
            if (virtualFile != null && !virtualFile.equals(lastFile)) {
              indicator.setText2(virtualFile.getPresentableUrl());
              lastFile = virtualFile;
            }
          }

          ProgressManager.getInstance().executeNonCancelableSection(() -> {
            try {
              if (replaceUsage(usage, replaceContext.getFindModel(), replaceContext.getExcludedSetCached(), false)) {
                replacedCount[0]++;
              }
            }
            catch (FindManager.MalformedReplacementStringException ex) {
              markAsMalformedReplacement(replaceContext, usage);
              success[0] = false;
            }
          });
        }
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    );
    success[0] &= result;
    replaceContext.getUsageView().removeUsagesBulk(usages);
    reportNumberReplacedOccurrences(myProject, replacedCount[0]);
    return success[0];
  }

  private static void markAsMalformedReplacement(ReplaceContext replaceContext, Usage usage) {
    replaceContext.getUsageView().excludeUsages(new Usage[]{usage});
  }

  public static void reportNumberReplacedOccurrences(Project project, int occurrences) {
    if (occurrences != 0) {
      final StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
      if (statusBar != null) {
        statusBar.setInfo(FindBundle.message("0.occurrences.replaced", occurrences));
      }
    }
  }

  public boolean replaceSingleUsage(@NotNull Usage usage, @NotNull FindModel findModel, @NotNull Set<Usage> excludedSet)
    throws FindManager.MalformedReplacementStringException {
    return ensureUsagesWritable(Collections.singleton(usage)) && replaceUsage(usage, findModel, excludedSet, false);
  }

  public boolean replaceUsage(@NotNull Usage usage, @NotNull FindModel findModel, @NotNull Set<Usage> excludedSet, boolean justCheck)
    throws FindManager.MalformedReplacementStringException {
    final Ref<FindManager.MalformedReplacementStringException> exceptionResult = Ref.create();
    final boolean result = WriteAction.compute(() -> {
      if (excludedSet.contains(usage)) {
        return false;
      }

      final Document document = ((UsageInfo2UsageAdapter)usage).getDocument();
      if (document == null || !document.isWritable()) return false;

      return ((UsageInfo2UsageAdapter)usage).processRangeMarkers(segment -> {
        final int textOffset = segment.getStartOffset();
        final int textEndOffset = segment.getEndOffset();
        final Ref<String> stringToReplace = Ref.create();
        try {
          if (!getStringToReplace(textOffset, textEndOffset, document, findModel, stringToReplace)) return true;
          if (!stringToReplace.isNull() && !justCheck) {
            document.replaceString(textOffset, textEndOffset, stringToReplace.get());
          }
        }
        catch (FindManager.MalformedReplacementStringException e) {
          exceptionResult.set(e);
          return false;
        }
        return true;
      });
    });

    if (!exceptionResult.isNull()) {
      throw exceptionResult.get();
    }
    return result;
  }

  private boolean getStringToReplace(int textOffset,
                                     int textEndOffset,
                                     Document document, FindModel findModel, Ref<? super String> stringToReplace)
    throws FindManager.MalformedReplacementStringException {
    if (textOffset < 0 || textOffset >= document.getTextLength()) {
      return false;
    }
    if (textEndOffset < 0 || textEndOffset > document.getTextLength()) {
      return false;
    }
    FindManager findManager = FindManager.getInstance(myProject);
    final CharSequence foundString = document.getCharsSequence().subSequence(textOffset, textEndOffset);
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    FindResult findResult =
      findManager.findString(document.getCharsSequence(), textOffset, findModel, file != null ? file.getVirtualFile() : null);
    if (!findResult.isStringFound() ||
        // find result should be in needed range
        !(findResult.getStartOffset() >= textOffset && findResult.getEndOffset() <= textEndOffset)) {
      return false;
    }

    stringToReplace.set(
      FindManager.getInstance(myProject).getStringToReplace(foundString.toString(), findModel, textOffset, document.getText()));

    return true;
  }

  private void replaceUsagesUnderCommand(@NotNull ReplaceContext replaceContext, @NotNull Set<? extends Usage> usagesSet,
                                         boolean replaceAll) {
    if (usagesSet.isEmpty()) {
      return;
    }

    final List<Usage> usages = new ArrayList<>(usagesSet);
    usages.sort(UsageViewImpl.USAGE_COMPARATOR_BY_FILE_AND_OFFSET);

    if (!ensureUsagesWritable(usages)) return;

    Runnable runnable = () -> {
      final boolean success = replaceUsages(replaceContext, usages);
      final UsageView usageView = replaceContext.getUsageView();

      if (closeUsageViewIfEmpty(usageView, success)) return;
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
        () -> IdeFocusManager.getGlobalInstance().requestFocus(usageView.getPreferredFocusableComponent(), true));
    };
    if (replaceAll) {
      FindModel findModel = replaceContext.getFindModel();
      LocalHistoryAction action =
        LocalHistory.getInstance().startAction(FindBundle.message("find.in.files.replace.all.local.history.action",
                                                                  findModel.getStringToFind(),
                                                                  findModel.getStringToReplace(),
                                                                  FindInProjectUtil.getTitleForScope(findModel)));
      try {
        CommandProcessor.getInstance().executeCommand(myProject, runnable, FindBundle.message("find.replace.command"), null);
      }
      finally {
        action.finish();
      }
    }
    else {
      CommandProcessor.getInstance().executeCommand(myProject, runnable, FindBundle.message("find.replace.command"), null);
    }

    replaceContext.invalidateExcludedSetCache();
  }

  private boolean ensureUsagesWritable(Collection<? extends Usage> selectedUsages) {
    Set<VirtualFile> files = new HashSet<>();
    for (Usage usage : selectedUsages) {
      final VirtualFile file = ((UsageInFile)usage).getFile();
      files.add(file);
    }

    return !ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(files).hasReadonlyFiles();
  }

  private boolean closeUsageViewIfEmpty(UsageView usageView, boolean success) {
    if (usageView.getUsages().isEmpty()) {
      usageView.close();
      return true;
    }
    if (!success) {
      NOTIFICATION_GROUP.createNotification(FindBundle.message("notification.content.one.or.more.malformed.replacement.strings"), MessageType.ERROR).notify(myProject);
    }
    return false;
  }

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled() {
    return !myIsFindInProgress && !FindInProjectManager.getInstance(myProject).isWorkInProgress();
  }

  private final class UsageSearcherFactory implements Factory<UsageSearcher> {
    private final FindModel myFindModelCopy;
    private final FindUsagesProcessPresentation myProcessPresentation;

    private UsageSearcherFactory(@NotNull FindModel findModelCopy,
                                 @NotNull FindUsagesProcessPresentation processPresentation) {
      myFindModelCopy = findModelCopy;
      myProcessPresentation = processPresentation;
    }

    @Override
    public UsageSearcher create() {
      return processor -> {
        try {
          myIsFindInProgress = true;

          FindInProjectUtil.findUsages(myFindModelCopy, myProject,
                                       new AdapterProcessor<>(processor, UsageInfo2UsageAdapter.CONVERTER),
                                       myProcessPresentation);
        }
        finally {
          myIsFindInProgress = false;
        }
      };
    }
  }
}
