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
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author peter
 */
public class MakeInferredAnnotationExplicit extends BaseIntentionAction {

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Make Inferred Annotations Explicit";
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, Editor editor, PsiFile file) {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiModifierListOwner owner = ExternalAnnotationsLineMarkerProvider.getAnnotationOwner(leaf);
    if (owner != null && !(owner instanceof PsiCompiledElement) && owner.getLanguage().isKindOf(JavaLanguage.INSTANCE) &&
        ModuleUtilCore.findModuleForPsiElement(file) != null &&
        PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5)) {
      final PsiAnnotation[] annotations = InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner);
      if (annotations.length > 0) {
        final String annos = StringUtil.join(annotations, new Function<PsiAnnotation, String>() {
          @Override
          public String fun(PsiAnnotation annotation) {
            final PsiJavaCodeReferenceElement nameRef = correctAnnotation(annotation).getNameReferenceElement();
            final String name = nameRef != null ? nameRef.getReferenceName() : annotation.getQualifiedName();
            return "@" + name + annotation.getParameterList().getText();
          }
        }, " ");
        setText("Insert '" + annos + "'");
        return true;
      }
    }
    
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiModifierListOwner owner = ExternalAnnotationsLineMarkerProvider.getAnnotationOwner(leaf);
    assert owner != null;
    final PsiModifierList modifierList = owner.getModifierList();
    assert modifierList != null;
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;

    if (!FileModificationService.getInstance().preparePsiElementForWrite(owner)) return;

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    for (PsiAnnotation inferred : InferredAnnotationsManager.getInstance(project).findInferredAnnotations(owner)) {
      final PsiAnnotation toInsert = correctAnnotation(inferred);
      final String qname = toInsert.getQualifiedName();
      assert qname != null;
      if (facade.findClass(qname, file.getResolveScope()) == null &&
          !InferNullityAnnotationsAction.addAnnotationsDependency(project, Collections.singleton(module), qname, getFamilyName())) {
        return;
      }
      
      WriteCommandAction.runWriteCommandAction(project, new Runnable() {
        @Override
        public void run() {
          DumbService.getInstance(project).withAlternativeResolveEnabled(new Runnable() {
            @Override
            public void run() {
              JavaCodeStyleManager.getInstance(project).shortenClassReferences(modifierList.addAfter(toInsert, null));
            }
          });
        }
      });
    }

    
  }

  @NotNull
  private static PsiAnnotation correctAnnotation(@NotNull PsiAnnotation annotation) {
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
