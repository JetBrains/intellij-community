// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.inferNullity.InferNullityAnnotationsAction;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public final class MakeInferredAnnotationExplicit extends BaseIntentionAction {
  private boolean myNeedToAddDependency;

  @Override
  public @Nls @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.make.inferred.annotations.explicit");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    final PsiElement leaf = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (leaf == null) return false;
    final PsiModifierListOwner owner = ObjectUtils.tryCast(leaf.getParent(), PsiModifierListOwner.class);
    return isAvailable(psiFile, owner);
  }

  public boolean isAvailable(PsiFile file, PsiModifierListOwner owner) {
    if (owner != null && owner.getLanguage().isKindOf(JavaLanguage.INSTANCE) && isWritable(owner) &&
        ModuleUtilCore.findModuleForPsiElement(file) != null &&
        PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, file)) {
      List<PsiAnnotation> annotations = getAnnotationsToAdd(owner);
      if (!annotations.isEmpty()) {
        String presentation = StreamEx.of(annotations)
          .map(MakeInferredAnnotationExplicit::getAnnotationPresentation)
          .joining(" ");
        setText(CommonQuickFixBundle.message("fix.insert.x", presentation));
        return true;
      }
    }
    
    return false;
  }

  private @Unmodifiable List<PsiAnnotation> filterAnnotations(PsiFile file, List<PsiAnnotation> annotations) {
    if (annotations.isEmpty() || !needToAddDependency(file, annotations)) return annotations;
    if (InferNullityAnnotationsAction.maySuggestAnnotationDependency(file.getProject())) {
      myNeedToAddDependency = true;
      return annotations;
    }
    return ContainerUtil.filter(annotations, anno -> !isJetBrainsAnnotation(anno));
  }

  private static @NotNull String getAnnotationPresentation(PsiAnnotation annotation) {
    final PsiJavaCodeReferenceElement nameRef = correctAnnotation(annotation).getNameReferenceElement();
    final String name = nameRef != null ? nameRef.getReferenceName() : annotation.getQualifiedName();
    return "@" + name + annotation.getParameterList().getText();
  }

  private static boolean isWritable(PsiModifierListOwner owner) {
    if (owner instanceof PsiCompiledElement) return false;

    VirtualFile vFile = PsiUtilCore.getVirtualFile(owner);
    return vFile != null && vFile.isInLocalFileSystem();
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    final PsiElement leaf = psiFile.findElementAt(editor.getCaretModel().getOffset());
    assert leaf != null;
    final PsiModifierListOwner owner = ObjectUtils.tryCast(leaf.getParent(), PsiModifierListOwner.class);
    assert owner != null;
    if (myNeedToAddDependency) {
      makeAnnotationsExplicit(project, psiFile, owner);
    } else {
      doMakeAnnotationExplicit(project, owner, getAnnotationsToAdd(owner));
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiElement leaf = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (leaf == null) return IntentionPreviewInfo.EMPTY;
    PsiModifierListOwner owner = ObjectUtils.tryCast(leaf.getParent(), PsiModifierListOwner.class);
    if (owner == null) return IntentionPreviewInfo.EMPTY;
    doMakeAnnotationExplicit(project, owner, getAnnotationsToAdd(owner));
    return IntentionPreviewInfo.DIFF;
  }

  /**
   * Explicitly adds inferred annotations to the code (if any). Creates write action inside, so should be run in write-thread.
   *
   * @param project current project
   * @param file file
   * @param owner annotation owner (e.g., PsiMethod)
   */
  public void makeAnnotationsExplicit(@NotNull Project project, PsiFile file, PsiModifierListOwner owner) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(owner)) return;
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;

    // Inferred annotations are non-physical, so we can pass them between actions
    List<PsiAnnotation> annotations = getAnnotationsToAdd(owner);
    if (needToAddDependency(file, annotations)) {
      SmartPsiElementPointer<PsiModifierListOwner> ownerPointer = SmartPointerManager.createPointer(owner);
      InferNullityAnnotationsAction.addAnnotationsDependency(project, Collections.singleton(module), AnnotationUtil.NOT_NULL, getFamilyName())
        .onSuccess(__ -> ApplicationManager.getApplication()
          .invokeLater(() -> doStartWriteAction(project, file, ownerPointer.getElement(), annotations),
                       ModalityState.nonModal(),
                       module.getDisposed()));
      return;
    }

    doStartWriteAction(project, file, owner, annotations);
  }

  private static boolean needToAddDependency(PsiFile file, List<PsiAnnotation> annotations) {
    return ContainerUtil.exists(annotations, MakeInferredAnnotationExplicit::isJetBrainsAnnotation) &&
           JavaPsiFacade.getInstance(file.getProject()).findClass(AnnotationUtil.NOT_NULL, file.getResolveScope()) == null;
  }

  private static boolean isJetBrainsAnnotation(PsiAnnotation anno) {
    String qualifiedName = anno.getQualifiedName();
    return qualifiedName != null && qualifiedName.startsWith("org.jetbrains.annotations.");
  }

  private void doStartWriteAction(@NotNull Project project,
                                  @NotNull PsiFile file,
                                  @Nullable PsiModifierListOwner owner,
                                  @NotNull List<PsiAnnotation> annotations) {
    if (owner == null) return;
    WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null,
                                             () -> DumbService.getInstance(project).withAlternativeResolveEnabled(
                                               () -> doMakeAnnotationExplicit(project, owner, annotations)), file);
  }

  private @Unmodifiable @NotNull List<PsiAnnotation> getAnnotationsToAdd(@NotNull PsiModifierListOwner owner) {
    List<PsiAnnotation> allAnnotations = StreamEx.of(InferredAnnotationsManager.getInstance(owner.getProject()).findInferredAnnotations(owner))
      .remove(DefaultInferredAnnotationProvider::isExperimentalInferredAnnotation)
      .map(MakeInferredAnnotationExplicit::correctAnnotation)
      .toList();
    return filterAnnotations(owner.getContainingFile(), allAnnotations);
  }

  private static void doMakeAnnotationExplicit(@NotNull Project project, @NotNull PsiModifierListOwner owner, @NotNull List<PsiAnnotation> annotations) {
    for (PsiAnnotation toInsert : annotations) {
      final String qname = toInsert.getQualifiedName();
      assert qname != null;
      
      PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(owner, qname);
      assert target != null;
      PsiElement element = target.addAnnotation(qname).replace(toInsert);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
    }
  }

  private static @NotNull PsiAnnotation correctAnnotation(@NotNull PsiAnnotation annotation) {
    Project project = annotation.getProject();
    NullableNotNullManager nnnm = NullableNotNullManager.getInstance(project);
    PsiAnnotation corrected = null;
    if (annotation.hasQualifiedName(AnnotationUtil.NULLABLE)) {
      corrected = createAnnotation(project, nnnm.getDefaultNullable());
    }
    else if (annotation.hasQualifiedName(AnnotationUtil.NOT_NULL)) {
      corrected = createAnnotation(project, nnnm.getDefaultNotNull());
    }
    return corrected != null ? corrected : annotation;
  }

  private static @Nullable PsiAnnotation createAnnotation(Project project, String qualifiedName) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    if (facade.findClass(qualifiedName, allScope) != null) {
      return facade.getElementFactory().createAnnotationFromText("@" + qualifiedName, null);
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return !myNeedToAddDependency;
  }
}
