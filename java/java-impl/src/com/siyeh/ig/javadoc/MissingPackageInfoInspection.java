// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javadoc;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveAnnotationToPackageInfoFileFix;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CreatePackageInfoAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.BaseSharedLocalInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PackageGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public final class MissingPackageInfoInspection extends PackageGlobalInspection {

  @Override
  public @Nullable LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalMissingPackageInfoInspection(this);
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkPackage(@NotNull RefPackage refPackage,
                                                           @NotNull AnalysisScope analysisScope,
                                                           @NotNull InspectionManager inspectionManager,
                                                           @NotNull GlobalInspectionContext globalInspectionContext) {
    final String packageName = refPackage.getQualifiedName();
    final Project project = globalInspectionContext.getProject();
    final PsiPackage aPackage = ReadAction.compute(() -> JavaPsiFacade.getInstance(project).findPackage(packageName));
    boolean needsPackageInfo =
      ReadAction.compute(() -> aPackage != null &&
                               MoveAnnotationToPackageInfoFileFix.getPackageInfoFile(aPackage) == null && aPackage.getClasses().length > 0);
    if (!needsPackageInfo) {
      return null;
    }
    if (aPackage != null && PsiUtil.isLanguageLevel5OrHigher(aPackage)) {
      return new CommonProblemDescriptor[] {
        inspectionManager.createProblemDescriptor(InspectionGadgetsBundle.message("missing.package.info.problem.descriptor", packageName))};
    }
    else {
      return new CommonProblemDescriptor[] {
        inspectionManager.createProblemDescriptor(InspectionGadgetsBundle.message("missing.package.html.problem.descriptor", packageName))};
    }
  }

  @SuppressWarnings("InspectionDescriptionNotFoundInspection") // TODO IJPL-166089
  private static class LocalMissingPackageInfoInspection extends BaseSharedLocalInspection<MissingPackageInfoInspection> {

    LocalMissingPackageInfoInspection(MissingPackageInfoInspection settingsDelegate) {
      super(settingsDelegate);
    }

    @Override
    protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
      return new InspectionGadgetsFix() {
        @Override
        public @NotNull String getFamilyName() {
          return InspectionGadgetsBundle.message("create.package.info.java.family.name");
        }

        @Override
        public boolean startInWriteAction() {
          return false;
        }

        @Override
        protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
          DataManager.getInstance()
                     .getDataContextFromFocusAsync()
                     .onSuccess(context -> {
                       final AnActionEvent event = new AnActionEvent(null, context, "", new Presentation(), ActionManager.getInstance(), 0);
                       new CreatePackageInfoAction().actionPerformed(event);
                     });
        }

        @Override
        public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
          Icon icon = FileTypeRegistry.getInstance().getFileTypeByFileName("package-info.java").getIcon();
          HtmlChunk fragment = HtmlChunk.fragment(HtmlChunk.text(getFamilyName()), HtmlChunk.icon("file", icon));
          return new IntentionPreviewInfo.Html(fragment);
        }
      };
    }

    @Override
    protected @NotNull String buildErrorString(Object... infos) {
      final PsiPackageStatement packageStatement = (PsiPackageStatement)infos[0];
      if (PsiUtil.isLanguageLevel5OrHigher(packageStatement)) {
        return InspectionGadgetsBundle.message("missing.package.info.problem.descriptor", packageStatement.getPackageName());
      }
      else {
        return InspectionGadgetsBundle.message("missing.package.html.problem.descriptor", packageStatement.getPackageName());
      }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
      return new BaseInspectionVisitor() {
        @Override
        public void visitJavaFile(@NotNull PsiJavaFile file) {
          final PsiPackageStatement packageStatement = file.getPackageStatement();
          if (packageStatement == null) {
            return;
          }
          final PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
          final PsiElement target = packageReference.resolve();
          if (!(target instanceof PsiPackage aPackage)) {
            return;
          }
          if (MoveAnnotationToPackageInfoFileFix.getPackageInfoFile(aPackage) != null) {
            return;
          }
          registerError(packageReference, packageStatement);
        }
      };
    }
  }
}
