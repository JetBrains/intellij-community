// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveClassesOrPackagesCallback;
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * @author Jeka,dsl
 */
public class MoveClassesOrPackagesProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(MoveClassesOrPackagesProcessor.class);

  private final PsiElement[] myElementsToMove;
  private boolean mySearchInComments;
  private boolean mySearchInNonJavaFiles;
  private final @NotNull PackageWrapper myTargetPackage;
  private final MoveCallback myMoveCallback;
  protected final @NotNull MoveDestination myMoveDestination;
  private final ModuleInfoUsageDetector myModuleInfoUsageDetector;
  protected NonCodeUsageInfo[] myNonCodeUsages;
  private boolean myOpenInEditor;
  private MultiMap<PsiElement, @Nls String> myConflicts;
  private List<UsageInfo> myUsagesToSkip;

  public MoveClassesOrPackagesProcessor(Project project,
                                        PsiElement[] elements,
                                        final @NotNull MoveDestination moveDestination,
                                        boolean searchInComments,
                                        boolean searchInNonJavaFiles,
                                        MoveCallback moveCallback) {
    super(project);
    myElementsToMove = MoveClassesOrPackagesUtil.collectElementsToMove(elements);
    myMoveDestination = moveDestination;
    myModuleInfoUsageDetector = ModuleInfoUsageDetector.createModifyUsageInstance(myProject, myElementsToMove, myMoveDestination);
    myTargetPackage = myMoveDestination.getTargetPackage();
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    PsiElement[] elements = myElementsToMove.clone();
    return new MoveMultipleElementsViewDescriptor(elements, MoveClassesOrPackagesUtil.getPackageName(myTargetPackage));
  }

  public boolean verifyValidPackageName() {
    String qName = myTargetPackage.getQualifiedName();
    if (!StringUtil.isEmpty(qName)) {
      PsiNameHelper helper = PsiNameHelper.getInstance(myProject);
      if (!helper.isQualifiedName(qName)) {
        Messages.showMessageDialog(myProject, JavaRefactoringBundle.message("invalid.target.package.name.specified"),
                                   JavaRefactoringBundle.message("move.classes.invalid.package.name.warning.message"),
                                   Messages.getErrorIcon());
        return false;
      }
    }
    return true;
  }

  public boolean isSearchInComments() {
    return mySearchInComments;
  }

  public boolean isSearchInNonJavaFiles() {
    return mySearchInNonJavaFiles;
  }

  public void setSearchInComments(boolean searchInComments) {
    mySearchInComments = searchInComments;
  }

  public void setSearchInNonJavaFiles(boolean searchInNonJavaFiles) {
    mySearchInNonJavaFiles = searchInNonJavaFiles;
  }


  @Override
  protected UsageInfo @NotNull [] findUsages() {
    MoveClassesOrPackagesUtil.UsagesContext usages = MoveClassesOrPackagesUtil.findUsagesInElements(
      myElementsToMove, myRefactoringScope, mySearchInComments, mySearchInNonJavaFiles, myTargetPackage);
    myConflicts = new MultiMap<>();
    myUsagesToSkip = usages.usagesToSkip();
    List<UsageInfo> allUsages = new ArrayList<>(usages.allUsages());
    MoveClassesOrPackagesUtil.collectConflicts(
      allUsages, myConflicts, myElementsToMove, myTargetPackage, myMoveDestination, myModuleInfoUsageDetector);
    return allUsages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  public static void detectConflicts(UsageInfo[] usageInfos,
                                     MultiMap<PsiElement, @DialogMessage String> conflicts,
                                     PsiElement @NotNull[] elementsToMove,
                                     @NotNull PackageWrapper targetPackage,
                                     @NotNull MoveDestination moveDestination) {
    MoveClassesOrPackagesUtil.detectConflicts(usageInfos, conflicts, elementsToMove, targetPackage, moveDestination);
  }

  public List<PsiElement> getElements() {
    return List.of(myElementsToMove);
  }

  public PackageWrapper getTargetPackage() {
    return myMoveDestination.getTargetPackage();
  }

  public void setOpenInEditor(boolean openInEditor) {
    myOpenInEditor = openInEditor;
  }

  @Override
  protected @Nullable String getRefactoringId() {
    return "refactoring.move";
  }

  @Override
  protected @Nullable RefactoringEventData getBeforeData() {
    return MoveClassesOrPackagesUtil.createBeforeData(myElementsToMove);
  }

  @Override
  protected @Nullable RefactoringEventData getAfterData(UsageInfo @NotNull [] usages) {
    RefactoringEventData data = new RefactoringEventData();
    data.addElements(myTargetPackage.getDirectories());
    data.addElement(JavaPsiFacade.getInstance(myProject).findPackage(myTargetPackage.getQualifiedName()));
    return data;
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    refUsages.set(MoveClassesOrPackagesUtil.extractAffectedUsages(Arrays.asList(refUsages.get()), myUsagesToSkip));
    return showConflicts(myConflicts, refUsages.get());
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length == myElementsToMove.length);
    System.arraycopy(elements, 0, myElementsToMove, 0, elements.length);
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    if (UsageViewUtil.reportNonRegularUsages(usages, myProject)) {
      return true;
    }
    else {
      return super.isPreviewUsages(usages);
    }
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    List<RefactoringElementListener> listeners =
      ContainerUtil.map(myElementsToMove, psiElement -> getTransaction().getElementListener(psiElement));

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setIndeterminate(false);
    }
    try {
      MoveClassesOrPackagesUtil.MoveElementsResult result =
        MoveClassesOrPackagesUtil.moveElements(myProject, myMoveDestination, myElementsToMove);

      List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<>();
      List<UsageInfo> codeUsages = new ArrayList<>();

      for (UsageInfo usage : usages) {
        if (!(usage instanceof MoveRenameUsageInfo)) continue;

        if (usage instanceof NonCodeUsageInfo) {
          nonCodeUsages.add((NonCodeUsageInfo) usage);
        } else {
          codeUsages.add(usage);
        }
      }

      CommonMoveUtil.retargetUsages(codeUsages.toArray(UsageInfo.EMPTY_ARRAY), result.oldToNewElementsMapping());
      myNonCodeUsages = nonCodeUsages.toArray(new NonCodeUsageInfo[0]);

      for (PsiElement element : result.movedElements()) {
        if (element instanceof PsiClass psiClass) {
          MoveClassesOrPackagesUtil.finishMoveClass(psiClass);
        }
      }

      modifyModuleStatementsInDescriptor(usages);

      MoveClassesOrPackagesUtil.afterMovement(listeners, result.movedElementPointers(), myElementsToMove);

      if (myOpenInEditor) {
        ApplicationManager.getApplication().invokeLater(() -> EditorHelper.openFilesInEditor(
          ContainerUtil.mapNotNull(result.movedElementPointers(), p -> p.getElement()).toArray(PsiElement.EMPTY_ARRAY)));
      }
    }
    catch (IncorrectOperationException e) {
      myNonCodeUsages = new NonCodeUsageInfo[0];
      RefactoringUIUtil.processIncorrectOperation(myProject, e);
    }
  }

  private void modifyModuleStatementsInDescriptor(UsageInfo @NotNull [] usages) {
    List<UsageInfo> allUsages = new SmartList<>(usages);
    allUsages.addAll(myModuleInfoUsageDetector.createUsageInfosForNewlyCreatedDirs());
    Map<PsiJavaModule, List<ModifyModuleStatementUsageInfo>> moduleStatementsByDescriptor = StreamEx.of(allUsages)
      .select(ModifyModuleStatementUsageInfo.class).groupingBy(usage -> usage.getModuleDescriptor());
    modifyModuleStatements(moduleStatementsByDescriptor);
  }

  public static void modifyModuleStatements(@NotNull Map<PsiJavaModule, List<ModifyModuleStatementUsageInfo>> moduleStatementsByDescriptor) {
    if (moduleStatementsByDescriptor.isEmpty()) return;
    MultiMap<PsiJavaModule, ModifyModuleStatementUsageInfo> lastDeletionUsageInfos = new MultiMap<>();
    for (var entry : moduleStatementsByDescriptor.entrySet()) {
      PsiJavaModule moduleDescriptor = entry.getKey();
      for (ModifyModuleStatementUsageInfo modifyStatementInfo : entry.getValue()) {
        if (modifyStatementInfo.getModuleStatement() == null) continue;
        if (modifyStatementInfo.isAddition()) {
          PsiUtil.addModuleStatement(moduleDescriptor, modifyStatementInfo.getModuleStatement());
        }
        else if (modifyStatementInfo.isLastDeletion()) {
          lastDeletionUsageInfos.putValue(moduleDescriptor, modifyStatementInfo);
        }
        else if (modifyStatementInfo.isDeletion()) {
          deleteModuleStatements(moduleDescriptor, Set.of(modifyStatementInfo.getModuleStatement().getText()));
        }
      }
    }
    if (lastDeletionUsageInfos.isEmpty()) return;
    PsiJavaModule firstModule = lastDeletionUsageInfos.keySet().iterator().next();
    Project project = firstModule.getProject();
    String projectPath = project.getBasePath();
    if (projectPath == null) return;
    MultiMap<String, String> packageStatementsByModulePath = MultiMap.createSet();
    for (var entry : lastDeletionUsageInfos.entrySet()) {
      for (ModifyModuleStatementUsageInfo usageInfo : entry.getValue()) {
        PsiPackageAccessibilityStatement packageStatement = usageInfo.getModuleStatement();
        if (packageStatement == null) continue;
        VirtualFile moduleFile = entry.getKey().getContainingFile().getVirtualFile();
        if (moduleFile != null) {
          packageStatementsByModulePath.putValue(moduleFile.getPath(), packageStatement.getText());
        }
      }
    }
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Remove redundant exports/opens")
      .createNotification(
        JavaRefactoringBundle.message("move.classes.or.packages.unused.exports.notification.title", lastDeletionUsageInfos.size()),
        createNotificationContent(projectPath, lastDeletionUsageInfos.keySet()),
        NotificationType.INFORMATION)
      .setListener((notification, event) -> {
        PsiJavaModule moduleDescriptor = findModuleByPath(project, event.getDescription());
        if (moduleDescriptor != null) {
          moduleDescriptor.navigate(true);
        }
      })
      .addAction(new NotificationAction(JavaRefactoringBundle.message("move.classes.or.packages.unused.exports.action.name")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          WriteCommandAction.writeCommandAction(project)
            .withName(JavaRefactoringBundle.message("move.classes.or.packages.unused.exports.command.name"))
            .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION)
            .withGlobalUndo()
            .run(() -> {
              for (var entry : packageStatementsByModulePath.entrySet()) {
                PsiJavaModule moduleDescriptor = findModuleByPath(project, entry.getKey());
                if (moduleDescriptor != null) {
                  deleteModuleStatements(moduleDescriptor, (Set<String>)entry.getValue());
                }
              }
            });
          notification.expire();
        }
      })
      .notify(project);
  }

  private static void deleteModuleStatements(@NotNull PsiJavaModule moduleDescriptor, @NotNull Set<String> packageStatementsText) {
    List<PsiPackageAccessibilityStatement> packageStatements = new SmartList<>();
    for (PsiPackageAccessibilityStatement exportStatement : moduleDescriptor.getExports()) {
      packageStatements.add(exportStatement);
    }
    for (PsiPackageAccessibilityStatement openStatement : moduleDescriptor.getOpens()) {
      packageStatements.add(openStatement);
    }
    for (PsiPackageAccessibilityStatement packageStatement : packageStatements) {
      if (packageStatementsText.contains(packageStatement.getText())) {
        packageStatement.delete();
      }
    }
  }

  private static @NlsSafe @NotNull String createNotificationContent(@NotNull String projectPath, @NotNull Set<PsiJavaModule> moduleDescriptors) {
    // we may have several JPMS-modules with the same name
    MultiMap<String, String> modulesPathsByName = new MultiMap<>();
    for (PsiJavaModule moduleDescriptor : moduleDescriptors) {
      VirtualFile moduleFile = moduleDescriptor.getContainingFile().getVirtualFile();
      if (moduleFile != null) {
        modulesPathsByName.putValue(moduleDescriptor.getName(), moduleFile.getPath());
      }
    }
    HtmlBuilder contentBuilder = new HtmlBuilder();
    for (var entry : modulesPathsByName.entrySet()) {
      @NlsSafe String moduleName = entry.getKey();
      Collection<String> modulePaths = entry.getValue();
      if (modulePaths.size() == 1) {
        contentBuilder.appendLink(modulePaths.iterator().next(), PsiJavaModule.MODULE_INFO_FILE + " (" + moduleName + ")").br();
        continue;
      }
      for (@NlsSafe String modulePath : modulePaths) {
        // here we reduce an absolute module path to relative path to place it in the notification content
        String relativeModulePath = FileUtil.getRelativePath(projectPath, modulePath, '/');
        contentBuilder.appendLink(modulePath, relativeModulePath + " (" + moduleName + ")").br();
      }
    }
    return contentBuilder.toString();
  }

  private static @Nullable PsiJavaModule findModuleByPath(@NotNull Project project, @NotNull String modulePath) {
    VirtualFile moduleFile = LocalFileSystem.getInstance().findFileByPath(modulePath);
    if (moduleFile == null) return null;
    return JavaModuleGraphUtil.findDescriptorByFile(moduleFile, project);
  }

  @Override
    protected void performPsiSpoilingRefactoring() {
    RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
    invokeMoveCallback();
  }

  private void invokeMoveCallback() {
    if (myMoveCallback != null) {
      if (myMoveCallback instanceof MoveClassesOrPackagesCallback) {
        ((MoveClassesOrPackagesCallback) myMoveCallback).classesOrPackagesMoved(myMoveDestination);
      }
      myMoveCallback.refactoringCompleted();
    }
  }

  @Override
  protected @NotNull String getCommandName() {
    String elements = RefactoringUIUtil.calculatePsiElementDescriptionList(myElementsToMove);
    String target = myTargetPackage.getQualifiedName();
    return JavaRefactoringBundle.message("move.classes.command", elements, target);
  }
}
