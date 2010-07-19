/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.refactoring;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.RefactoringListenerManagerImpl;
import com.intellij.refactoring.listeners.impl.RefactoringTransaction;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public abstract class BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.BaseRefactoringProcessor");
  public static final Runnable EMPTY_CALLBACK = EmptyRunnable.getInstance();
  protected final Project myProject;

  private RefactoringTransaction myTransaction;
  private boolean myIsPreviewUsages;
  protected Runnable myPrepareSuccessfulSwingThreadCallback = EMPTY_CALLBACK;


  protected BaseRefactoringProcessor(Project project) {
    this(project, null);
  }

  protected BaseRefactoringProcessor(Project project, @Nullable Runnable prepareSuccessfulCallback) {
    myProject = project;
    myPrepareSuccessfulSwingThreadCallback = prepareSuccessfulCallback;
  }

  protected abstract UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages);

  /**
   * Is called inside atomic action.
   */
  @NotNull
  protected abstract UsageInfo[] findUsages();

  /**
   * is called when usage search is re-run.
   *
   * @param elements - refreshed elements that are returned by UsageViewDescriptor.getElements()
   */
  protected abstract void refreshElements(PsiElement[] elements);

  /**
   * Is called inside atomic action.
   *
   * @param refUsages usages to be filtered
   * @return true if preprocessed successfully
   */
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    prepareSuccessful();
    return true;
  }

  /**
   * Is called inside atomic action.
   */
  protected boolean isPreviewUsages(UsageInfo[] usages) {
    return myIsPreviewUsages;
  }

  protected boolean isPreviewUsages() {
    return myIsPreviewUsages;
  }


  public void setPreviewUsages(boolean isPreviewUsages) {
    myIsPreviewUsages = isPreviewUsages;
  }

  public void setPrepareSuccessfulSwingThreadCallback(Runnable prepareSuccessfulSwingThreadCallback) {
    myPrepareSuccessfulSwingThreadCallback = prepareSuccessfulSwingThreadCallback;
  }

  protected RefactoringTransaction getTransaction() {
    return myTransaction;
  }

  /**
   * Is called in a command and inside atomic action.
   */
  protected abstract void performRefactoring(UsageInfo[] usages);

  protected abstract String getCommandName();

  protected void doRun() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final Ref<UsageInfo[]> refUsages = new Ref<UsageInfo[]>();
    final Ref<Language> refErrorLanguage = new Ref<Language>();
    final Ref<Boolean> refProcessCanceled = new Ref<Boolean>();
    final Ref<Boolean> dumbModeOccured = new Ref<Boolean>();

    final Runnable findUsagesRunnable = new Runnable() {
      public void run() {
        try {
          refUsages.set(findUsages());
        }
        catch (UnknownReferenceTypeException e) {
          refErrorLanguage.set(e.getElementLanguage());
        }
        catch (ProcessCanceledException e) {
          refProcessCanceled.set(Boolean.TRUE);
        }
        catch (IndexNotReadyException e) {
          dumbModeOccured.set(Boolean.TRUE);
        }
      }
    };

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(findUsagesRunnable, RefactoringBundle.message("progress.text"), true, myProject)) {
      return;
    }

    if (!refErrorLanguage.isNull()) {
      Messages.showErrorDialog(myProject, RefactoringBundle.message("unsupported.refs.found", refErrorLanguage.get().getDisplayName()), RefactoringBundle.message("error.title"));
      return;
    }
    if (!dumbModeOccured.isNull()) {
      DumbService.getInstance(myProject).showDumbModeNotification("Usage search is not available until indices are ready");
      return;
    }
    if (!refProcessCanceled.isNull()) {
      Messages.showErrorDialog(myProject, "Index corruption detected. Please retry the refactoring - indexes will be rebuilt automatically",
                               RefactoringBundle.message("error.title"));
      return;
    }

    assert !refUsages.isNull(): "Null usages from processor " + this;
    if (!preprocessUsages(refUsages)) return;
    final UsageInfo[] usages = refUsages.get();
    assert usages != null;
    UsageViewDescriptor descriptor = createUsageViewDescriptor(usages);

    boolean isPreview = isPreviewUsages(usages);
    if (!isPreview) {
      isPreview = !ensureElementsWritable(usages, descriptor) || UsageViewUtil.hasReadOnlyUsages(usages);
      if (isPreview) {
        WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("readonly.occurences.found"));
      }
    }
    if (isPreview) {
      previewRefactoring(usages);
    }
    else {
      execute(usages);
    }
  }

  protected void previewRefactoring(final UsageInfo[] usages) {
    final UsageViewDescriptor viewDescriptor = createUsageViewDescriptor(usages);
    final PsiElement[] elements = viewDescriptor.getElements();
    final PsiElement2UsageTargetAdapter[] targets = PsiElement2UsageTargetAdapter.convert(elements);
    Factory<UsageSearcher> factory = new Factory<UsageSearcher>() {
      public UsageSearcher create() {
        return new UsageSearcher() {
          public void generate(final Processor<Usage> processor) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                for (int i = 0; i < elements.length; i++) {
                  elements[i] = targets[i].getElement();
                }
                refreshElements(elements);
              }
            });
            final Ref<UsageInfo[]> refUsages = new Ref<UsageInfo[]>();
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                refUsages.set(findUsages());
              }
            });
            final Usage[] usages = UsageInfo2UsageAdapter.convert(refUsages.get());

            for (Usage usage : usages) {
              processor.process(usage);
            }
          }
        };
      }
    };

    showUsageView(viewDescriptor, factory, usages);
  }

  private boolean ensureElementsWritable(@NotNull final UsageInfo[] usages, final UsageViewDescriptor descriptor) {
    Set<PsiElement> elements = new THashSet<PsiElement>();
    for (UsageInfo usage : usages) {
      assert usage != null: "Found null element in usages array";
      PsiElement element = usage.getElement();
      if (element != null) elements.add(element);
    }
    elements.addAll(getElementsToWrite(descriptor));
    return ensureFilesWritable(myProject, elements);
  }

  private static boolean ensureFilesWritable(final Project project, Collection<? extends PsiElement> elements) {
    PsiElement[] psiElements = elements.toArray(new PsiElement[elements.size()]);
    return CommonRefactoringUtil.checkReadOnlyStatus(project, psiElements);
  }

  protected void execute(final UsageInfo[] usages) {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        Collection<UsageInfo> usageInfos = new HashSet<UsageInfo>(Arrays.asList(usages));
        doRefactoring(usageInfos);
        if (isGlobalUndoAction()) CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
      }
    }, getCommandName(), null, getUndoConfirmationPolicy());
  }

  protected boolean isGlobalUndoAction() {
    return PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext()) == null;
  }

  protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
    return UndoConfirmationPolicy.DEFAULT;
  }

  private static UsageViewPresentation createPresentation(UsageViewDescriptor descriptor, final Usage[] usages) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText(RefactoringBundle.message("usageView.tabText"));
    presentation.setTargetsNodeText(descriptor.getProcessedElementsHeader());
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));
    int codeUsageCount = 0;
    int nonCodeUsageCount = 0;
    Set<PsiFile> codeFiles = new HashSet<PsiFile>();
    Set<PsiFile> nonCodeFiles = new HashSet<PsiFile>();

    for (Usage usage : usages) {
      if (usage instanceof PsiElementUsage) {
        final PsiElementUsage elementUsage = (PsiElementUsage)usage;
        if (elementUsage.isNonCodeUsage()) {
          nonCodeUsageCount++;
          nonCodeFiles.add(elementUsage.getElement().getContainingFile());
        }
        else {
          codeUsageCount++;
          codeFiles.add(elementUsage.getElement().getContainingFile());
        }
      }
    }
    codeFiles.remove(null);
    nonCodeFiles.remove(null);

    presentation.setCodeUsagesString(descriptor.getCodeReferencesText(codeUsageCount, codeFiles.size()));
    presentation.setNonCodeUsagesString(descriptor.getCommentReferencesText(nonCodeUsageCount, nonCodeFiles.size()));
    return presentation;
  }

  private void showUsageView(final UsageViewDescriptor viewDescriptor, final Factory<UsageSearcher> factory, final UsageInfo[] usageInfos) {
    UsageViewManager viewManager = UsageViewManager.getInstance(myProject);

    final PsiElement[] initialElements = viewDescriptor.getElements();
    final UsageTarget[] targets = PsiElement2UsageTargetAdapter.convert(initialElements);
    final Ref<Usage[]> convertUsagesRef = new Ref<Usage[]>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos));
      }
    }, "Preprocess usages", true, myProject)) return;

    if (convertUsagesRef.isNull()) return;

    final Usage[] usages = convertUsagesRef.get();

    final UsageViewPresentation presentation = createPresentation(viewDescriptor, usages);

    final UsageView usageView = viewManager.showUsages(targets, usages, presentation, factory);

    final Runnable refactoringRunnable = new Runnable() {
      public void run() {
        Set<UsageInfo> usagesToRefactor = getUsageInfosToRefactor(usageView);
        if (ensureElementsWritable(usagesToRefactor.toArray(new UsageInfo[usagesToRefactor.size()]), viewDescriptor)) {
          doRefactoring(usagesToRefactor);
        }
      }
    };

    String canNotMakeString = RefactoringBundle.message("usageView.need.reRun");

    usageView.addPerformOperationAction(refactoringRunnable, getCommandName(), canNotMakeString, RefactoringBundle.message("usageView.doAction"));
  }

  private static Set<UsageInfo> getUsageInfosToRefactor(final UsageView usageView) {
    Set<Usage> excludedUsages = usageView.getExcludedUsages();

    Set<UsageInfo> usageInfos = new HashSet<UsageInfo>();
    for (Usage usage : usageView.getUsages()) {
      if (usage instanceof UsageInfo2UsageAdapter && !excludedUsages.contains(usage)) {
        UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();
        usageInfos.add(usageInfo);
      }
    }
    return usageInfos;
  }

  private void doRefactoring(@NotNull Collection<UsageInfo> usageInfoSet) {
   for (Iterator<UsageInfo> iterator = usageInfoSet.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      final PsiElement element = usageInfo.getElement();
      if (element == null || !element.isWritable()) {
        iterator.remove();
      }
    }

    LocalHistoryAction action = LocalHistory.getInstance().startAction(getCommandName());

    final UsageInfo[] writableUsageInfos = usageInfoSet.toArray(new UsageInfo[usageInfoSet.size()]);
    try {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      RefactoringListenerManagerImpl listenerManager = (RefactoringListenerManagerImpl)RefactoringListenerManager.getInstance(myProject);
      myTransaction = listenerManager.startTransaction();
      final Map<RefactoringHelper, Object> preparedData = new HashMap<RefactoringHelper, Object>();
      final Runnable prepareHelpersRunnable = new Runnable() {
        public void run() {
          for (RefactoringHelper helper : Extensions.getExtensions(RefactoringHelper.EP_NAME)) {
            preparedData.put(helper, helper.prepareOperation(writableUsageInfos));
          }
        }
      };

      ProgressManager.getInstance().runProcessWithProgressSynchronously(prepareHelpersRunnable, "Prepare ...", false, myProject);

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().assertWriteAccessAllowed();
          performRefactoring(writableUsageInfos);
        }
      });

      for(Map.Entry<RefactoringHelper, Object> e: preparedData.entrySet()) {
        //noinspection unchecked
        e.getKey().performOperation(myProject, e.getValue());
      }
      myTransaction.commit();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          performPsiSpoilingRefactoring();
        }
      });
    }
    finally {
      action.finish();
    }

    int count = writableUsageInfos.length;
    if (count > 0) {
      WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("statusBar.refactoring.result", count));
    }
    else {
      if (!isPreviewUsages(writableUsageInfos)) {
        WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("statusBar.noUsages"));
      }
    }
  }

  /**
   * Refactorings that spoil PSI (write something directly to documents etc.) should
   * do that in this method.<br>
   * This method is called immediately after
   * <code>{@link #performRefactoring(UsageInfo[])}</code>.
   */
  protected void performPsiSpoilingRefactoring() {

  }

  protected void prepareSuccessful() {
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      // make sure that dialog is closed in swing thread
      try {
        GuiUtils.runOrInvokeAndWait(myPrepareSuccessfulSwingThreadCallback);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
    }
  }

  /**
   * Override in subclasses
   */
  protected void prepareTestRun() {

  }

  public final void run() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      testRun();
    }
    else {
      doRun();
    }
  }

  private void testRun() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    prepareTestRun();
    Ref<UsageInfo[]> refUsages = new Ref<UsageInfo[]>(findUsages());
    preprocessUsages(refUsages);

    final UsageInfo[] usages = refUsages.get();
    UsageViewDescriptor descriptor = createUsageViewDescriptor(usages);
    if (!ensureElementsWritable(usages, descriptor)) return;

    RefactoringListenerManagerImpl listenerManager = (RefactoringListenerManagerImpl)RefactoringListenerManager.getInstance(myProject);
    myTransaction = listenerManager.startTransaction();
    Map<RefactoringHelper, Object> preparedData = new HashMap<RefactoringHelper, Object>();
    for(RefactoringHelper helper: Extensions.getExtensions(RefactoringHelper.EP_NAME)) {
      preparedData.put(helper, helper.prepareOperation(usages));
    }
    performRefactoring(usages);
    for(Map.Entry<RefactoringHelper, Object> e: preparedData.entrySet()) {
      //noinspection unchecked
      e.getKey().performOperation(myProject, e.getValue());
    }
    myTransaction.commit();
    performPsiSpoilingRefactoring();
  }

  public static class ConflictsInTestsException extends RuntimeException {
    private final Collection<? extends String> messages;

    private static boolean myTestIgnore = false;

    public ConflictsInTestsException(Collection<? extends String> messages) {
      this.messages = messages;
    }

    public static void setTestIgnore(boolean myIgnore) {
      myTestIgnore = myIgnore;
    }

    public static boolean isTestIgnore() {
      return myTestIgnore;
    }

    public Collection<String> getMessages() {
        List<String> result = new ArrayList<String>(messages);
        for (int i = 0; i < messages.size(); i++) {
          result.set(i, result.get(i).replaceAll("<[^>]+>", ""));
        }
        return result;
      }

    @Override
    public String getMessage() {
      return StringUtil.join(messages, "\n");
    }
  }

  @Deprecated
  protected boolean showConflicts(final MultiMap<PsiElement, String> conflicts) {
    return showConflicts(conflicts, null);
  }

  protected boolean showConflicts(final MultiMap<PsiElement, String> conflicts, final UsageInfo[] usages) {
    if (!conflicts.isEmpty() && ApplicationManager.getApplication().isUnitTestMode()) {
      throw new ConflictsInTestsException(conflicts.values());
    }

    if (myPrepareSuccessfulSwingThreadCallback != null && !conflicts.isEmpty()) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts, usages == null ? null : new Runnable() {
        public void run() {
          execute(usages);
        }
      });
      conflictsDialog.show();
      if (!conflictsDialog.isOK()) {
        if (conflictsDialog.isShowConflicts()) prepareSuccessful();
        return false;
      }
    }

    prepareSuccessful();
    return true;
  }

  @NotNull
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
    return Arrays.asList(descriptor.getElements());
  }

  public static class UnknownReferenceTypeException extends RuntimeException {
    private final Language myElementLanguage;

    public UnknownReferenceTypeException(final Language elementLanguage) {
      myElementLanguage = elementLanguage;
    }

    public Language getElementLanguage() {
      return myElementLanguage;
    }
  }
}
