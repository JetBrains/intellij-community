// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import java.util.Collections;

/**
 * @author peter
 */
public class MakeInferredAnnotationExplicit extends BaseIntentionAction {

  @Nls
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.make.inferred.annotations.explicit");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    if (leaf == null) return false;
    final PsiModifierListOwner owner = ObjectUtils.tryCast(leaf.getParent(), PsiModifierListOwner.class);
    return isAvailable(project, file, owner);
  }

  public boolean isAvailable(@NotNull Project project, PsiFile file, PsiModifierListOwner owner) {
    if (owner != null && owner.getLanguage().isKindOf(JavaLanguage.INSTANCE) && isWritable(owner) &&
        ModuleUtilCore.findModuleForPsiElement(file) != null &&
        PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5)) {
      String annotations = StreamEx.of(InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner))
                                   .remove(DefaultInferredAnnotationProvider::isExperimentalInferredAnnotation)
                                   .map(MakeInferredAnnotationExplicit::getAnnotationPresentation)
                                   .joining(" ");
      if (!annotations.isEmpty()) {
        setText(JavaBundle.message("intention.text.insert.0.annotation", annotations));
        return true;
      }
    }
    
    return false;
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
  public void invoke(final @NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    assert leaf != null;
    final PsiModifierListOwner owner = ObjectUtils.tryCast(leaf.getParent(), PsiModifierListOwner.class);
    assert owner != null;
    doMakeAnnotationExplicit(project, owner);
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

    String qualifiedName = NotNull.class.getCanonicalName();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    if (facade.findClass(qualifiedName, file.getResolveScope()) == null) {
      Promise<Void> promise =
        InferNullityAnnotationsAction.addAnnotationsDependency(project, Collections.singleton(module), qualifiedName, getFamilyName());
      if (promise != null) {
        SmartPsiElementPointer<PsiModifierListOwner> ownerPointer = SmartPointerManager.createPointer(owner);
        promise.onSuccess(__ -> ApplicationManager.getApplication().invokeLater(() -> doStartWriteAction(project, file, ownerPointer.getElement()),
                                                                                ModalityState.NON_MODAL,
                                                                                module.getDisposed()));
      }
      return;
    }

    doStartWriteAction(project, file, owner);
  }

  private void doStartWriteAction(@NotNull Project project, PsiFile file, PsiModifierListOwner owner) {
    WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null,
                                             () -> DumbService.getInstance(project).withAlternativeResolveEnabled(
                                               () -> doMakeAnnotationExplicit(project, owner)), file);
  }

  private static void doMakeAnnotationExplicit(@NotNull Project project, PsiModifierListOwner owner) {
    for (PsiAnnotation inferred : InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner)) {
      if (DefaultInferredAnnotationProvider.isExperimentalInferredAnnotation(inferred)) continue;
      final PsiAnnotation toInsert = correctAnnotation(inferred);
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
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    NullableNotNullManager nnnm = NullableNotNullManager.getInstance(project);
    if (AnnotationUtil.NULLABLE.equals(annotation.getQualifiedName()) && 
        facade.findClass(nnnm.getDefaultNullable(), allScope) != null) {
      return facade.getElementFactory().createAnnotationFromText("@" + nnnm.getDefaultNullable(), null);
    }
    
    if (AnnotationUtil.NOT_NULL.equals(annotation.getQualifiedName()) && 
        facade.findClass(nnnm.getDefaultNotNull(), allScope) != null) {
      return facade.getElementFactory().createAnnotationFromText("@" + nnnm.getDefaultNotNull(), null);
    }
    return annotation;
  }
}
