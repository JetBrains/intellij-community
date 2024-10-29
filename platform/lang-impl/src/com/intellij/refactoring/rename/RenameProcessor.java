// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.LangBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ConflictsDialogBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RelatedUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.util.*;

import static com.intellij.openapi.util.NlsContexts.Command;
import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public class RenameProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(RenameProcessor.class);

  protected final LinkedHashMap<PsiElement, String> myAllRenames = new LinkedHashMap<>();

  private @NotNull PsiElement myPrimaryElement;
  private String myNewName = null;

  private boolean mySearchInComments;
  private boolean mySearchTextOccurrences;
  protected boolean myForceShowPreview;

  private @Command String myCommandName;

  private NonCodeUsageInfo[] myNonCodeUsages = new NonCodeUsageInfo[0];
  private final List<AutomaticRenamerFactory> myRenamerFactories = new ArrayList<>();
  private final List<AutomaticRenamer> myRenamers = new ArrayList<>();
  private final List<UnresolvableCollisionUsageInfo> mySkippedUsages = new ArrayList<>();

  public RenameProcessor(@NotNull Project project,
                         @NotNull PsiElement element,
                         @NotNull String newName,
                         boolean isSearchInComments,
                         boolean isSearchTextOccurrences) {
    this(project, element, newName, GlobalSearchScope.projectScope(project), isSearchInComments, isSearchTextOccurrences);
  }

  public RenameProcessor(@NotNull Project project,
                         @NotNull PsiElement element,
                         @NotNull String newName,
                         @NotNull SearchScope refactoringScope,
                         boolean isSearchInComments,
                         boolean isSearchTextOccurrences) {
    super(project, refactoringScope, null);
    myPrimaryElement = element;

    RenameUtil.assertNonCompileElement(element);

    mySearchInComments = isSearchInComments;
    mySearchTextOccurrences = isSearchTextOccurrences;

    setNewName(newName);

    logScopeStatistics(RenameUsagesCollector.started);
  }

  public Set<PsiElement> getElements() {
    return Collections.unmodifiableSet(myAllRenames.keySet());
  }

  public String getNewName(PsiElement element) {
    return myAllRenames.get(element);
  }

  public void addRenamerFactory(AutomaticRenamerFactory factory) {
    if (!myRenamerFactories.contains(factory)) {
      myRenamerFactories.add(factory);
    }
  }

  public void removeRenamerFactory(AutomaticRenamerFactory factory) {
    myRenamerFactories.remove(factory);
  }

  @Override
  public void doRun() {
    if (!myPrimaryElement.isValid()) return;
    prepareRenaming(myPrimaryElement, myNewName, myAllRenames);

    super.doRun();
  }

  public void prepareRenaming(final @NotNull PsiElement element, final String newName, final LinkedHashMap<PsiElement, String> allRenames) {
    final List<RenamePsiElementProcessor> processors = RenamePsiElementProcessor.allForElement(element);
    myForceShowPreview = false;
    for (RenamePsiElementProcessor processor : processors) {
      processor.prepareRenaming(element, newName, allRenames);
      myForceShowPreview |= processor.forcesShowPreview();
    }
  }

  private @Nullable String getHelpID() {
    return RenamePsiElementProcessor.forElement(myPrimaryElement).getHelpID(myPrimaryElement);
  }

  @Override
  public boolean preprocessUsages(final @NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();

    RenameUtil.addConflictDescriptions(usagesIn, conflicts);
    RenamePsiElementProcessor.forElement(myPrimaryElement).findExistingNameConflicts(myPrimaryElement, myNewName, conflicts, myAllRenames);
    if (!conflicts.isEmpty()) {

      final RefactoringEventData conflictData = new RefactoringEventData();
      conflictData.putUserData(RefactoringEventData.CONFLICTS_KEY, conflicts.values());
      myProject.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .conflictsDetected("refactoring.rename", conflictData);

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        if (!ConflictsInTestsException.isTestIgnore()) throw new ConflictsInTestsException(conflicts.values());
        return true;
      }
      ConflictsDialogBase conflictsDialog = prepareConflictsDialog(conflicts, refUsages.get());
      if (!conflictsDialog.showAndGet()) {
        if (conflictsDialog.isShowConflicts()) prepareSuccessful();
        return false;
      }
    }

    final List<UsageInfo> variableUsages = new ArrayList<>();
    if (!myRenamers.isEmpty()) {
      if (!findRenamedVariables(variableUsages)) return false;
      final LinkedHashMap<PsiElement, String> renames = new LinkedHashMap<>();
      for (final AutomaticRenamer renamer : myRenamers) {
        final List<? extends PsiNamedElement> variables = renamer.getElements();
        for (final PsiNamedElement variable : variables) {
          final String newName = renamer.getNewName(variable);
          if (newName != null) {
            addElement(variable, newName);
            prepareRenaming(variable, newName, renames);
          }
        }
      }
      if (!renames.isEmpty()) {
        for (PsiElement element : renames.keySet()) {
          RenameUtil.assertNonCompileElement(element);
        }
        myAllRenames.putAll(renames);
        final Runnable runnable = () -> {
          for (final Map.Entry<PsiElement, String> entry : renames.entrySet()) {
            final UsageInfo[] usages =
              ReadAction.compute(() -> RenameUtil.findUsages(
                entry.getKey(), entry.getValue(), myRefactoringScope,
                mySearchInComments, mySearchTextOccurrences, myAllRenames));
            Collections.addAll(variableUsages, usages);
          }
        };
        if (!ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("searching.for.variables"), true, myProject)) {
          return false;
        }
      }
    }

    final int[] choice = myAllRenames.size() > 1 ? new int[]{-1} : null;
    try {
      for (Iterator<Map.Entry<PsiElement, String>> iterator = myAllRenames.entrySet().iterator(); iterator.hasNext(); ) {
        Map.Entry<PsiElement, String> entry = iterator.next();
        if (entry.getKey() instanceof PsiFile file) {
          final PsiDirectory containingDirectory = file.getContainingDirectory();
          if (CopyFilesOrDirectoriesHandler.checkFileExist(containingDirectory, choice, file, entry.getValue(),
                                                           RefactoringBundle.message("command.name.rename"))) {
            iterator.remove();
            continue;
          }
        }
        RenameUtil.checkRename(entry.getKey(), entry.getValue());
      }
    }
    catch (IncorrectOperationException e) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("rename.title"), e.getMessage(), getHelpID(), myProject);
      return false;
    }

    final Set<UsageInfo> usagesSet = ContainerUtil.newLinkedHashSet(usagesIn);
    usagesSet.addAll(variableUsages);
    final List<UnresolvableCollisionUsageInfo> conflictUsages = RenameUtil.removeConflictUsages(usagesSet);
    if (conflictUsages != null) {
      mySkippedUsages.addAll(conflictUsages);
    }
    refUsages.set(usagesSet.toArray(UsageInfo.EMPTY_ARRAY));

    prepareSuccessful();
    return PsiElementRenameHandler.canRename(myProject, null, myPrimaryElement);
  }

  private boolean findRenamedVariables(final List<UsageInfo> variableUsages) {
    for (Iterator<AutomaticRenamer> iterator = myRenamers.iterator(); iterator.hasNext(); ) {
      AutomaticRenamer automaticVariableRenamer = iterator.next();
      if (!automaticVariableRenamer.hasAnythingToRename()) continue;
      if (!showAutomaticRenamingDialog(automaticVariableRenamer)) {
        iterator.remove();
      }
    }

    final Runnable runnable = () -> ApplicationManager.getApplication().runReadAction(() -> {
      Tracer tracer = TelemetryManager.Companion.getTracer(new Scope("RenameProcessorScope"));
      TraceKt.use(tracer.spanBuilder("RenameProcessor"), __ -> {
        for (final AutomaticRenamer renamer : myRenamers) {
          renamer.findUsages(variableUsages, mySearchInComments, mySearchTextOccurrences, mySkippedUsages, myAllRenames);
        }
        return null;
      });
    });

    return ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("searching.for.variables"), true, myProject);
  }

  protected boolean showAutomaticRenamingDialog(AutomaticRenamer automaticVariableRenamer) {
    if (!RefactoringSettings.getInstance().RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG) return false;
    if (ApplicationManager.getApplication().isUnitTestMode() || Boolean.getBoolean("ide.performance.skip.refactoring.dialogs")) {
      for (PsiNamedElement element : automaticVariableRenamer.getElements()) {
        automaticVariableRenamer.setRename(element, automaticVariableRenamer.getNewName(element));
      }
      return true;
    }
    final AutomaticRenamingDialog dialog = new AutomaticRenamingDialog(myProject, automaticVariableRenamer);
    return dialog.showAndGet();
  }

  public void addElement(@NotNull PsiElement element, @NotNull String newName) {
    RenameUtil.assertNonCompileElement(element);
    myAllRenames.put(element, newName);
  }

  private void setNewName(@NotNull String newName) {
    myNewName = newName;
    myAllRenames.put(myPrimaryElement, newName);
    myCommandName = RefactoringBundle
      .message("renaming.0.1.to.2", UsageViewUtil.getType(myPrimaryElement), DescriptiveNameUtil.getDescriptiveName(myPrimaryElement),
               newName);
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new RenameViewDescriptor(myAllRenames);
  }

  @Override
  public UsageInfo @NotNull [] findUsages() {
    myRenamers.clear();
    List<UsageInfo> result = new ArrayList<>();

    for (PsiElement element : new ArrayList<>(myAllRenames.keySet())) {
      if (element == null) {
        LOG.error("primary: " + myPrimaryElement + "; renamers: " + myRenamers);
        continue;
      }

      String newName = myAllRenames.get(element);
      UsageInfo[] usages = RenameUtil.findUsages(element, newName, myRefactoringScope,
                                                 mySearchInComments, mySearchTextOccurrences, myAllRenames);
      List<UsageInfo> usagesList = Arrays.asList(usages);
      result.addAll(usagesList);

      for (AutomaticRenamerFactory factory : myRenamerFactories) {
        if (factory.isApplicable(element)) {
          myRenamers.add(factory.createRenamer(element, newName, usagesList));
        }
      }

      for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
        if (factory.getOptionName() == null && factory.isApplicable(element)) {
          myRenamers.add(factory.createRenamer(element, newName, usagesList));
        }
      }
    }

    UsageInfo[] usageInfos = result.toArray(UsageInfo.EMPTY_ARRAY);
    usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos);
    return usageInfos;
  }

  public boolean hasNonCodeUsages() {
    for (PsiElement element : new ArrayList<>(myAllRenames.keySet())) {
      String newName = myAllRenames.get(element);
      if (RenameUtil.hasNonCodeUsages(element, newName, myRefactoringScope, mySearchInComments, mySearchTextOccurrences)) return true;
    }
    return false;
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length > 0);
    myPrimaryElement = elements[0];

    final Iterator<String> newNames = myAllRenames.values().iterator();
    LinkedHashMap<PsiElement, String> newAllRenames = new LinkedHashMap<>();
    for (PsiElement resolved : elements) {
      newAllRenames.put(resolved, newNames.next());
    }
    myAllRenames.clear();
    myAllRenames.putAll(newAllRenames);
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    return myForceShowPreview || super.isPreviewUsages(usages) || UsageViewUtil.reportNonRegularUsages(usages, myProject);
  }

  @Override
  protected @Nullable String getRefactoringId() {
    return "refactoring.rename";
  }

  @Override
  protected @Nullable RefactoringEventData getBeforeData() {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(myPrimaryElement);
    return data;
  }

  @Override
  protected @Nullable RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(myPrimaryElement);
    return data;
  }

  @Override
  public void performRefactoring(UsageInfo @NotNull [] usages) {
    logScopeStatistics(RenameUsagesCollector.executed);

    List<Runnable> postRenameCallbacks = new ArrayList<>();

    MultiMap<RefactoringElementListener, SmartPsiElementPointer<PsiElement>> renameEvents = MultiMap.createLinked();

    List<UsageInfo> usagesList = Arrays.asList(usages);
    MultiMap<PsiElement, UsageInfo> classified = classifyUsages(myAllRenames.keySet(), usagesList);

    NonCodeUsageInfo[] nonCodeUsages =
      ContainerUtil.filterIsInstance(usagesList, NonCodeUsageInfo.class).toArray(new NonCodeUsageInfo[0]);

    for (final PsiElement element : myAllRenames.keySet()) {
      if (!element.isValid()) {
        LOG.error(new PsiInvalidElementAccessException(element));
        continue;
      }
      String newName = myAllRenames.get(element);

      final RefactoringElementListener elementListener = getTransaction().getElementListener(element);
      final RenamePsiElementProcessor renamePsiElementProcessor = RenamePsiElementProcessor.forElement(element);
      Runnable postRenameCallback = renamePsiElementProcessor.getPostRenameCallback(element, newName, elementListener);
      Collection<UsageInfo> infos = classified.get(element);
      try {
        RenameUtil.registerUndoableRename(element, elementListener);
        renamePsiElementProcessor.renameElement(element, newName, infos.toArray(UsageInfo.EMPTY_ARRAY), new RefactoringElementListener() {
          @Override
          public void elementMoved(@NotNull PsiElement newElement) {
            throw new UnsupportedOperationException();
          }

          @Override
          public void elementRenamed(@NotNull PsiElement newElement) {
            if (!newElement.isValid()) return;
            renameEvents.putValue(elementListener, SmartPointerManager.createPointer(newElement));
          }
        });
      }
      catch (final IncorrectOperationException e) {
        RenameUtil.showErrorMessage(e, element, myProject);
        return;
      }
      if (postRenameCallback != null) {
        postRenameCallbacks.add(postRenameCallback);
      }
    }

    myNonCodeUsages = nonCodeUsages;

    afterRename(postRenameCallbacks, renameEvents);
  }

  private void afterRename(List<? extends Runnable> postRenameCallbacks,
                           MultiMap<RefactoringElementListener, SmartPsiElementPointer<PsiElement>> renameEvents) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    for (Map.Entry<RefactoringElementListener, Collection<SmartPsiElementPointer<PsiElement>>> entry : renameEvents.entrySet()) {
      for (SmartPsiElementPointer<PsiElement> pointer : entry.getValue()) {
        PsiElement element = pointer.getElement();
        if (element != null) {
          entry.getKey().elementRenamed(element);
        }
      }
    }

    for (Runnable runnable : postRenameCallbacks) {
      runnable.run();
    }

    if (!mySkippedUsages.isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
        ApplicationManager.getApplication().invokeLater(() -> {
          IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(myProject);
          if (ideFrame != null) {
            StatusBarEx statusBar = (StatusBarEx)ideFrame.getStatusBar();
            String message = LangBundle.message("popup.content.unable.to.rename.certain.usages");
            HyperlinkListener listener = e -> {
              if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
              String skipped =
                StringUtil.join(mySkippedUsages, unresolvableCollisionUsageInfo -> unresolvableCollisionUsageInfo.getDescription(), "<br>");
              Messages.showMessageDialog(RefactoringBundle.message("rename.not.all.usages.message", skipped),
                                         RefactoringBundle.message("rename.not.all.usages.title"), null);
            };
            statusBar.notifyProgressByBalloon(MessageType.WARNING, message, null, listener);
          }
        }, ModalityState.nonModal());
      }
    }
  }

  @Override
  protected void performPsiSpoilingRefactoring() {
    RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
  }

  @Override
  protected @NotNull String getCommandName() {
    return myCommandName;
  }

  public static MultiMap<PsiElement, UsageInfo> classifyUsages(Collection<? extends PsiElement> elements,
                                                               Collection<? extends UsageInfo> usages) {
    final MultiMap<PsiElement, UsageInfo> result = new MultiMap<>();
    for (UsageInfo usage : usages) {
      LOG.assertTrue(usage instanceof MoveRenameUsageInfo);
      if (shouldSkip(usage)) {
        continue;
      }
      MoveRenameUsageInfo usageInfo = (MoveRenameUsageInfo)usage;
      if (usage instanceof RelatedUsageInfo) {
        final PsiElement relatedElement = ((RelatedUsageInfo)usage).getRelatedElement();
        if (elements.contains(relatedElement)) {
          result.putValue(relatedElement, usage);
        }
      }
      else {
        PsiElement referenced = usageInfo.getReferencedElement();
        if (elements.contains(referenced)) {
          result.putValue(referenced, usage);
        }
        else if (referenced != null) {
          PsiElement indirect = referenced.getNavigationElement();
          if (elements.contains(indirect)) {
            result.putValue(indirect, usage);
          }
        }
      }
    }
    return result;
  }

  //filter out implicit references (e.g. from derived class to super class' default constructor)
  private static boolean shouldSkip(UsageInfo usage) {
    return usage.getReference() instanceof LightElement;
  }

  public Collection<String> getNewNames() {
    return myAllRenames.values();
  }

  public void setSearchInComments(boolean value) {
    mySearchInComments = value;
  }

  public void setSearchTextOccurrences(boolean searchTextOccurrences) {
    mySearchTextOccurrences = searchTextOccurrences;
  }

  public boolean isSearchInComments() {
    return mySearchInComments;
  }

  public boolean isSearchTextOccurrences() {
    return mySearchTextOccurrences;
  }

  public void setCommandName(@Command String commandName) {
    myCommandName = commandName;
  }

  private void logScopeStatistics(VarargEventId eventId) {
    Class<? extends RenamePsiElementProcessor> renameProcessor = RenamePsiElementProcessor.forElement(myPrimaryElement).getClass();
    eventId.log(
      myProject,
      RenameUsagesCollector.scopeType.with(getStatisticsCompatibleScopeName()),
      RenameUsagesCollector.searchInComments.with(isSearchInComments()),
      RenameUsagesCollector.searchInTextOccurrences.with(isSearchTextOccurrences()),
      RenameUsagesCollector.renameProcessor.with(renameProcessor),
      EventFields.Language.with(myPrimaryElement.getLanguage())
    );
  }

  private RenameScopeType getStatisticsCompatibleScopeName() {
    String displayName = myRefactoringScope.getDisplayName();
    if (displayName.equals(ProjectScope.getProjectFilesScopeName())) {
      return RenameScopeType.Project;
    }

    if (displayName.equals(GlobalSearchScopesCore.getProjectTestFilesScopeName())) {
      return RenameScopeType.Tests;
    }

    if (displayName.equals(GlobalSearchScopesCore.getProjectProductionFilesScopeName())) {
      return RenameScopeType.Production;
    }

    if (myRefactoringScope instanceof LocalSearchScope) {
      return RenameScopeType.CurrentFile;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(myPrimaryElement);
    if (module != null && myRefactoringScope.equals(module.getModuleScope())) {
      return RenameScopeType.Module;
    }

    if (!PluginInfoDetectorKt.getPluginInfo(myRefactoringScope.getClass()).isSafeToReport()) {
      return RenameScopeType.ThirdParty;
    }

    return RenameScopeType.Unknown;
  }
}