/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.inferNullity.InferNullityAnnotationsAction;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
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
    makeAnnotationsExplicit(project, file, owner);
  }

  public void makeAnnotationsExplicit(@NotNull Project project, PsiFile file, PsiModifierListOwner owner) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;

    if (!FileModificationService.getInstance().preparePsiElementForWrite(owner)) return;

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    for (PsiAnnotation inferred : InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner)) {
      if (DefaultInferredAnnotationProvider.isExperimentalInferredAnnotation(inferred)) continue;
      final PsiAnnotation toInsert = correctAnnotation(inferred);
      final String qname = toInsert.getQualifiedName();
      assert qname != null;
      if (facade.findClass(qname, file.getResolveScope()) == null &&
          !InferNullityAnnotationsAction.addAnnotationsDependency(project, Collections.singleton(module), qname, getFamilyName())) {
        return;
      }

      WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> DumbService.getInstance(project).withAlternativeResolveEnabled(
        () -> {
          PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(owner, qname);
          assert target != null;
          PsiElement element = target.addAnnotation(qname).replace(toInsert);
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
        }), file);
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

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
