// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor;
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor;
import com.intellij.codeInspection.deprecation.DeprecationInspectionBase;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.uast.UastVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;

import javax.swing.*;
import java.util.List;

/**
 * This class can be extended by inspections that should report usage of elements annotated with some particular annotation(s).
 */
public abstract class AnnotatedElementInspectionBase extends LocalInspectionTool {
  public boolean myIgnoreInsideImports = true;


  @NotNull
  protected abstract List<String> getAnnotations();

  @NotNull
  @Override
  public JPanel createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.ignore.inside.imports"), this, "myIgnoreInsideImports");
  }

  @NotNull
  protected abstract AnnotatedApiUsageProcessor buildAnnotatedApiUsageProcessor(@NotNull ProblemsHolder holder);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!isApplicable(holder.getFile(), holder.getProject())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    AnnotatedApiUsageProcessor annotatedApiProcessor = buildAnnotatedApiUsageProcessor(holder);
    AnnotatedApiUsageProcessorBridge processorBridge = new AnnotatedApiUsageProcessorBridge(
      myIgnoreInsideImports, getAnnotations(), annotatedApiProcessor
    );
    return ApiUsageUastVisitor.createPsiElementVisitor(processorBridge);
  }

  private boolean isApplicable(@Nullable PsiFile file, @Nullable Project project) {
    if (file == null || project == null) {
      return false;
    }

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = file.getResolveScope();
    for (String annotation : getAnnotations()) {
      if (javaPsiFacade.findClass(annotation, scope) != null) {
        return true;
      }
    }

    return false;
  }

  protected static String getPresentableText(@NotNull PsiElement psiElement) {
    return DeprecationInspectionBase.getPresentableName(psiElement);
  }

  protected static boolean isLibraryElement(@NotNull PsiElement element) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return true;
    }
    VirtualFile containingVirtualFile = PsiUtilCore.getVirtualFile(element);
    return containingVirtualFile != null && ProjectFileIndex.getInstance(element.getProject()).isInLibraryClasses(containingVirtualFile);
  }

  private static final class AnnotatedApiUsageProcessorBridge implements ApiUsageProcessor {
    private final boolean myIgnoreInsideImports;
    private final List<String> myAnnotations;
    private final AnnotatedApiUsageProcessor myAnnotatedApiProcessor;

    private AnnotatedApiUsageProcessorBridge(boolean ignoreInsideImports,
                                             @NotNull List<String> annotations,
                                             @NotNull AnnotatedApiUsageProcessor annotatedApiProcessor) {
      myIgnoreInsideImports = ignoreInsideImports;
      myAnnotations = annotations;
      myAnnotatedApiProcessor = annotatedApiProcessor;
    }

    @Override
    public void processImportReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target) {
      if (!myIgnoreInsideImports) {
        maybeProcessAnnotatedTarget(sourceNode, target);
      }
    }

    @Override
    public void processReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target, @Nullable UExpression qualifier) {
      maybeProcessAnnotatedTarget(sourceNode, target);
    }

    @Override
    public void processConstructorInvocation(@NotNull UElement sourceNode,
                                                   @NotNull PsiClass instantiatedClass,
                                                   @Nullable PsiMethod constructor,
                                                   @Nullable UClass subclassDeclaration) {
      if (constructor != null) {
        maybeProcessAnnotatedTarget(sourceNode, constructor);
      }
    }

    private void maybeProcessAnnotatedTarget(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target) {
      List<PsiAnnotation> annotations = AnnotationUtil.findAllAnnotations(target, myAnnotations, false);
      if (annotations.isEmpty()) {
        return;
      }
      myAnnotatedApiProcessor.processAnnotatedTarget(sourceNode, target, annotations);
    }
  }
}