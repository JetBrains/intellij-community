/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.find.replaceInProject;

import com.intellij.find.*;
import com.intellij.find.actions.FindInPathAction;
import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.DataManager;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.AdapterProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.*;

public class ReplaceInProjectManager {
  static final NotificationGroup NOTIFICATION_GROUP = FindInPathAction.NOTIFICATION_GROUP;

  private final Project myProject;
  private boolean myIsFindInProgress = false;

  public static ReplaceInProjectManager getInstance(Project project) {
    return ServiceManager.getService(project, ReplaceInProjectManager.class);
  }

  public ReplaceInProjectManager(Project project) {
    myProject = project;
  }

  public static boolean hasReadOnlyUsages(final Collection<Usage> usages) {
    for (Usage usage : usages) {
      if (usage.isReadOnly()) return true;
    }

    return false;
  }

  static class ReplaceContext {
    private final UsageView usageView;
    private final FindModel findModel;
    private Set<Usage> excludedSet;

    ReplaceContext(@NotNull UsageView usageView, @NotNull FindModel findModel) {
      this.usageView = usageView;
      this.findModel = findModel;
    }

    @NotNull
    public FindModel getFindModel() {
      return findModel;
    }

    @NotNull
    public UsageView getUsageView() {
      return usageView;
    }

    @NotNull
    public Set<Usage> getExcludedSetCached() {
      if (excludedSet == null) excludedSet = usageView.getExcludedUsages();
      return excludedSet;
    }

    public void invalidateExcludedSetCache() {
      excludedSet = null;
    }
  }

  /**
   * @param model would be used for replacing if not null, otherwise shared (project-level) model would be used
   */
  public void replaceInProject(@NotNull DataContext dataContext, @Nullable FindModel model) {
    final FindManager findManager = FindManager.getInstance(myProject);
    final FindModel findModel;
    if (model == null) {
      final boolean isOpenInNewTabEnabled;
      final boolean toOpenInNewTab;
      final Content selectedContent = com.intellij.usageView.UsageViewManager.getInstance(myProject).getSelectedContent(true);
      if (selectedContent != null && selectedContent.isPinned()) {
        toOpenInNewTab = true;
        isOpenInNewTabEnabled = false;
      }
      else {
        toOpenInNewTab = FindSettings.getInstance().isShowResultsInSeparateView();
        isOpenInNewTabEnabled = com.intellij.usageView.UsageViewManager.getInstance(myProject).getReusableContentsCount() > 0;
      }

      findModel = findManager.getFindInProjectModel().clone();
      findModel.setReplaceState(true);
      findModel.setOpenInNewTabVisible(true);
      findModel.setOpenInNewTabEnabled(isOpenInNewTabEnabled);
      findModel.setOpenInNewTab(toOpenInNewTab);
      FindInProjectUtil.setDirectoryName(findModel, dataContext);
      FindInProjectUtil.initStringToFindFromDataContext(findModel, dataContext);
    }
    else {
      findModel = model;
    }

    findManager.showFindDialog(findModel, () -> {
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

      final UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(findModel.isOpenInNewTab(), findModelCopy);
      final FindUsagesProcessPresentation processPresentation = FindInProjectUtil.setupProcessPresentation(myProject, true, presentation);
      processPresentation.setShowFindOptionsPrompt(findModel.isPromptOnReplace());

      UsageSearcherFactory factory = new UsageSearcherFactory(findModelCopy, processPresentation);
      searchAndShowUsages(manager, factory, findModelCopy, presentation, processPresentation, findManager);
    });
  }

  public void searchAndShowUsages(@NotNull UsageViewManager manager,
                                  @NotNull Factory<UsageSearcher> usageSearcherFactory,
                                  @NotNull FindModel findModelCopy,
                                  @NotNull FindManager findManager) {
    final UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(true, findModelCopy);
    final FindUsagesProcessPresentation processPresentation = FindInProjectUtil.setupProcessPresentation(myProject, true, presentation);

    searchAndShowUsages(manager, usageSearcherFactory, findModelCopy, presentation, processPresentation, findManager);
  }

  private static class ReplaceInProjectTarget extends FindInProjectUtil.StringUsageTarget {
    public ReplaceInProjectTarget(@NotNull Project project, @NotNull FindModel findModel) {
      super(project, findModel);
    }

    @NotNull
    @Override
    public String getLongDescriptiveName() {
      UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(false, myFindModel);
      return "Replace " + StringUtil.decapitalize(presentation.getToolwindowTitle()) + " with '" + myFindModel.getStringToReplace() + "'";
    }

    @Override
    public KeyboardShortcut getShortcut() {
      return ActionManager.getInstance().getKeyboardShortcut("ReplaceInPath");
    }

    @Override
    public void showSettings() {
      Content selectedContent = com.intellij.usageView.UsageViewManager.getInstance(myProject).getSelectedContent(true);
      JComponent component = selectedContent == null ? null : selectedContent.getComponent();
      ReplaceInProjectManager findInProjectManager = getInstance(myProject);
      findInProjectManager.replaceInProject(DataManager.getInstance().getDataContext(component), myFindModel);
    }
  }

  public void searchAndShowUsages(@NotNull UsageViewManager manager,
                                  @NotNull Factory<UsageSearcher> usageSearcherFactory,
                                  @NotNull final FindModel findModelCopy,
                                  @NotNull UsageViewPresentation presentation,
                                  @NotNull FindUsagesProcessPresentation processPresentation,
                                  final FindManager findManager) {
    presentation.setMergeDupLinesAvailable(false);
    final ReplaceContext[] context = new ReplaceContext[1];
    final ReplaceInProjectTarget target = new ReplaceInProjectTarget(myProject, findModelCopy);
    ((FindManagerImpl)FindManager.getInstance(myProject)).getFindUsagesManager().addToHistory(target);
    manager.searchAndShowUsages(new UsageTarget[]{target},
                                usageSearcherFactory, processPresentation, presentation, new UsageViewManager.UsageViewStateListener() {
        @Override
        public void usageViewCreated(@NotNull UsageView usageView) {
          context[0] = new ReplaceContext(usageView, findModelCopy);
          addReplaceActions(context[0]);
          usageView.setReRunActivity(
            () -> searchAndShowUsages(manager, usageSearcherFactory, findModelCopy, presentation, processPresentation, findManager));
        }

        @Override
        public void findingUsagesFinished(final UsageView usageView) {
          if (context[0] != null && !processPresentation.isShowFindOptionsPrompt()) {
            TransactionGuard.submitTransaction(myProject, () -> {
              replaceUsagesUnderCommand(context[0], usageView.getUsages());
              context[0].invalidateExcludedSetCache();
            });
          }
        }
      });
  }

  private void replaceWithPrompt(final ReplaceContext replaceContext) {
    final List<Usage> _usages = replaceContext.getUsageView().getSortedUsages();

    if (hasReadOnlyUsages(_usages)) {
      WindowManager.getInstance().getStatusBar(myProject)
        .setInfo(FindBundle.message("find.replace.occurrences.found.in.read.only.files.status"));
      return;
    }

    final Usage[] usages = _usages.toArray(new Usage[_usages.size()]);

    //usageView.expandAll();
    for (int i = 0; i < usages.length; ++i) {
      final Usage usage = usages[i];
      final UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();

      final PsiElement elt = usageInfo.getElement();
      if (elt == null) continue;
      final PsiFile psiFile = elt.getContainingFile();
      if (!psiFile.isWritable()) continue;

      final VirtualFile virtualFile = psiFile.getVirtualFile();

      Runnable selectOnEditorRunnable = () -> {
        if (virtualFile != null && ReadAction.compute(() -> virtualFile.isValid()).booleanValue()) {

          if (usage.isValid()) {
            usage.highlightInEditor();
            replaceContext.getUsageView().selectUsages(new Usage[]{usage});
          }
        }
      };

      String path = ReadAction.compute(() -> virtualFile != null ? virtualFile.getPath() : null);
      CommandProcessor.getInstance()
        .executeCommand(myProject, selectOnEditorRunnable, FindBundle.message("find.replace.select.on.editor.command"), null);
      String title = FindBundle.message("find.replace.found.usage.title", i + 1, usages.length, path);

      int result;
      try {
        replaceUsage(usage, replaceContext.getFindModel(), replaceContext.getExcludedSetCached(), true);
        result = FindManager.getInstance(myProject).showPromptDialog(replaceContext.getFindModel(), title);
      }
      catch (FindManager.MalformedReplacementStringException e) {
        markAsMalformedReplacement(replaceContext, usage);
        result = FindManager.getInstance(myProject).showMalformedReplacementPrompt(replaceContext.getFindModel(), title, e);
      }

      if (result == FindManager.PromptResult.CANCEL) {
        return;
      }
      if (result == FindManager.PromptResult.SKIP) {
        continue;
      }

      final int currentNumber = i;
      if (result == FindManager.PromptResult.OK) {
        final Ref<Boolean> success = Ref.create();
        Runnable runnable = () -> success.set(replaceUsageAndRemoveFromView(usage, replaceContext));
        CommandProcessor.getInstance().executeCommand(myProject, runnable, FindBundle.message("find.replace.command"), null);
        if (closeUsageViewIfEmpty(replaceContext.getUsageView(), success.get())) {
          return;
        }
      }

      if (result == FindManager.PromptResult.SKIP_ALL_IN_THIS_FILE) {
        int j;
        for (j = i + 1; j < usages.length; ++j) {
          final PsiElement nextElt = ((UsageInfo2UsageAdapter)usages[j]).getUsageInfo().getElement();
          if (nextElt == null) continue;
          if (nextElt.getContainingFile() == psiFile) continue;
          break;
        }
        i = j - 1;
      }

      if (result == FindManager.PromptResult.ALL_IN_THIS_FILE) {
        final int[] nextNumber = new int[1];

        Runnable runnable = () -> {
          int j = currentNumber;
          boolean success = true;
          for (; j < usages.length; j++) {
            final Usage usage1 = usages[j];
            final UsageInfo usageInfo1 = ((UsageInfo2UsageAdapter)usage1).getUsageInfo();

            final PsiElement elt1 = usageInfo1.getElement();
            if (elt1 == null) continue;
            PsiFile otherPsiFile = elt1.getContainingFile();
            if (!otherPsiFile.equals(psiFile)) {
              break;
            }
            if (!replaceUsageAndRemoveFromView(usage1, replaceContext)) {
              success = false;
            }
          }
          closeUsageViewIfEmpty(replaceContext.getUsageView(), success);
          nextNumber[0] = j;
        };

        CommandProcessor.getInstance().executeCommand(myProject, runnable, FindBundle.message("find.replace.command"), null);

        //noinspection AssignmentToForLoopParameter
        i = nextNumber[0] - 1;
      }

      if (result == FindManager.PromptResult.ALL_FILES) {
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          final boolean success = replaceUsages(replaceContext, _usages);
          closeUsageViewIfEmpty(replaceContext.getUsageView(), success);
        }, FindBundle.message("find.replace.command"), null);
        break;
      }
    }
  }

  private boolean replaceUsageAndRemoveFromView(Usage usage, ReplaceContext replaceContext) {
    try {
      if (replaceUsage(usage, replaceContext.getFindModel(), replaceContext.getExcludedSetCached(), false)) {
        replaceContext.getUsageView().removeUsage(usage);
      }
    }
    catch (FindManager.MalformedReplacementStringException e) {
      markAsMalformedReplacement(replaceContext, usage);
      return false;
    }
    return true;
  }

  public boolean showReplaceAllConfirmDialog(String usagesCount, String stringToFind, String filesCount, String stringToReplace) {
    return Messages.YES == MessageDialogBuilder.yesNo(
      FindBundle.message("find.replace.all.confirmation.title"),
      FindBundle.message("find.replace.all.confirmation", usagesCount, StringUtil.escapeXml(stringToFind), filesCount,
                         StringUtil.escapeXml(stringToReplace)))
      .yesText(Messages.OK_BUTTON)
      .noText(Messages.CANCEL_BUTTON).show();
  }

  private void addReplaceActions(final ReplaceContext replaceContext) {
    final AbstractAction replaceAction = new AbstractAction(FindBundle.message("find.replace.all.action")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        Set<Usage> usages = replaceContext.getUsageView().getUsages();
        Set<VirtualFile> files = new HashSet<>();
        if (usages.isEmpty()) return;
        for (Usage usage : usages) {
          if (usage instanceof UsageInfo2UsageAdapter) {
            files.add(((UsageInfo2UsageAdapter)usage).getFile());
          }
        }
        if (files.size() < 2 || showReplaceAllConfirmDialog(
          "" + usages.size(),
          replaceContext.getFindModel().getStringToFind(),
          "" + files.size(),
          replaceContext.getFindModel().getStringToReplace())) {
          replaceUsagesUnderCommand(replaceContext, usages);
        }
      }

      @Override
      public boolean isEnabled() {
        return !replaceContext.getUsageView().getUsages().isEmpty();
      }
    };
    replaceContext.getUsageView().addButtonToLowerPane(replaceAction);

    final AbstractAction replaceSelectedAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        replaceUsagesUnderCommand(replaceContext, replaceContext.getUsageView().getSelectedUsages());
      }

      @Override
      public Object getValue(String key) {
        return Action.NAME.equals(key)
               ? FindBundle.message("find.replace.selected.action", replaceContext.getUsageView().getSelectedUsages().size())
               : super.getValue(key);
      }

      @Override
      public boolean isEnabled() {
        return !replaceContext.getUsageView().getSelectedUsages().isEmpty();
      }
    };

    replaceContext.getUsageView().addButtonToLowerPane(replaceSelectedAction);
  }

  private boolean replaceUsages(@NotNull ReplaceContext replaceContext, @NotNull Collection<Usage> usages) {
    if (!ensureUsagesWritable(replaceContext, usages)) {
      return true;
    }

    int[] replacedCount = {0};
    final boolean[] success = {true};

    success[0] &= ((ApplicationImpl)ApplicationManager.getApplication()).runWriteActionWithCancellableProgressInDispatchThread(
      FindBundle.message("find.replace.all.confirmation.title"),
      myProject,
      null,
      indicator -> {
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
      }
    );

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

  public boolean replaceUsage(@NotNull final Usage usage,
                              @NotNull final FindModel findModel,
                              @NotNull final Set<Usage> excludedSet,
                              final boolean justCheck)
    throws FindManager.MalformedReplacementStringException {
    final Ref<FindManager.MalformedReplacementStringException> exceptionResult = Ref.create();
    final boolean result = WriteAction.compute(() -> {
      if (excludedSet.contains(usage)) {
        return false;
      }

      final Document document = ((UsageInfo2UsageAdapter)usage).getDocument();
      if (!document.isWritable()) return false;

      boolean result1 = ((UsageInfo2UsageAdapter)usage).processRangeMarkers(segment -> {
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
      return result1;
    });

    if (!exceptionResult.isNull()) {
      throw exceptionResult.get();
    }
    return result;
  }

  private boolean getStringToReplace(int textOffset,
                                     int textEndOffset,
                                     Document document, FindModel findModel, Ref<String> stringToReplace)
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

  private void replaceUsagesUnderCommand(@NotNull final ReplaceContext replaceContext, @NotNull final Set<Usage> usagesSet) {
    if (usagesSet.isEmpty()) {
      return;
    }

    final List<Usage> usages = new ArrayList<>(usagesSet);
    Collections.sort(usages, UsageViewImpl.USAGE_COMPARATOR);

    if (!ensureUsagesWritable(replaceContext, usages)) return;

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final boolean success = replaceUsages(replaceContext, usages);
      final UsageView usageView = replaceContext.getUsageView();

      if (closeUsageViewIfEmpty(usageView, success)) return;
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(usageView.getComponent(), true);
      });
    }, FindBundle.message("find.replace.command"), null);

    replaceContext.invalidateExcludedSetCache();
  }

  private boolean ensureUsagesWritable(ReplaceContext replaceContext, Collection<Usage> selectedUsages) {
    Set<VirtualFile> readOnlyFiles = null;
    for (final Usage usage : selectedUsages) {
      final VirtualFile file = ((UsageInFile)usage).getFile();

      if (file != null && !file.isWritable()) {
        if (readOnlyFiles == null) readOnlyFiles = new HashSet<>();
        readOnlyFiles.add(file);
      }
    }

    if (readOnlyFiles != null) {
      ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(VfsUtilCore.toVirtualFileArray(readOnlyFiles));
    }

    if (hasReadOnlyUsages(selectedUsages)) {
      int result = Messages.showOkCancelDialog(replaceContext.getUsageView().getComponent(),
                                               FindBundle.message("find.replace.occurrences.in.read.only.files.prompt"),
                                               FindBundle.message("find.replace.occurrences.in.read.only.files.title"),
                                               Messages.getWarningIcon());
      if (result != Messages.OK) {
        return false;
      }
    }
    return true;
  }

  private boolean closeUsageViewIfEmpty(UsageView usageView, boolean success) {
    if (usageView.getUsages().isEmpty()) {
      usageView.close();
      return true;
    }
    if (!success) {
      NOTIFICATION_GROUP.createNotification("One or more malformed replacement strings", MessageType.ERROR).notify(myProject);
    }
    return false;
  }

  public boolean isWorkInProgress() {
    return myIsFindInProgress;
  }

  public boolean isEnabled() {
    return !myIsFindInProgress && !FindInProjectManager.getInstance(myProject).isWorkInProgress();
  }

  private class UsageSearcherFactory implements Factory<UsageSearcher> {
    private final FindModel myFindModelCopy;
    private final FindUsagesProcessPresentation myProcessPresentation;

    private UsageSearcherFactory(@NotNull FindModel findModelCopy,
                                 @NotNull FindUsagesProcessPresentation processPresentation) {
      myFindModelCopy = findModelCopy;
      myProcessPresentation = processPresentation;
    }

    @Override
    public UsageSearcher create() {
      return new UsageSearcher() {

        @Override
        public void generate(@NotNull final Processor<Usage> processor) {
          try {
            myIsFindInProgress = true;

            FindInProjectUtil.findUsages(myFindModelCopy, myProject,
                                         new AdapterProcessor<>(processor, UsageInfo2UsageAdapter.CONVERTER),
                                         myProcessPresentation);
          }
          finally {
            myIsFindInProgress = false;
          }
        }
      };
    }
  }
}
