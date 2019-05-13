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
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MakeExternalAnnotationExplicit extends BaseIntentionAction {

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Make External Annotations Explicit";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiModifierListOwner owner = ExternalAnnotationsLineMarkerProvider.getAnnotationOwner(leaf);
    if (owner != null && owner.getLanguage().isKindOf(JavaLanguage.INSTANCE) && isWritable(owner) &&
        ModuleUtilCore.findModuleForPsiElement(file) != null &&
        PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5)) {
      final PsiAnnotation[] annotations = getAnnotations(project, owner);
      if (annotations.length > 0) {
        final String annos = StringUtil.join(annotations, annotation -> {
          final PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
          final String name = nameRef != null ? nameRef.getReferenceName() : annotation.getQualifiedName();
          return "@" + name + annotation.getParameterList().getText();
        }, " ");
        setText("Insert '" + annos + "'");
        return true;
      }
    }
   
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiModifierListOwner owner = ExternalAnnotationsLineMarkerProvider.getAnnotationOwner(leaf);
    assert owner != null;
    final PsiModifierList modifierList = owner.getModifierList();
    assert modifierList != null;
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;

    ExternalAnnotationsManager externalAnnotationsManager = ExternalAnnotationsManager.getInstance(project);

    if (!FileModificationService.getInstance().preparePsiElementsForWrite(getFilesToWrite(file, owner, externalAnnotationsManager))) return;

    for (PsiAnnotation anno : getAnnotations(project, owner)) {
      final String qname = anno.getQualifiedName();
      assert qname != null;
      externalAnnotationsManager.deannotate(owner, qname);
      
      WriteCommandAction.runWriteCommandAction(project, () -> DumbService.getInstance(project).withAlternativeResolveEnabled(
        () -> JavaCodeStyleManager.getInstance(project).shortenClassReferences(modifierList.addAfter(anno, null))));
    }

    
  }

  public static List<PsiFile> getFilesToWrite(PsiFile file,
                                              PsiModifierListOwner owner,
                                              ExternalAnnotationsManager externalAnnotationsManager) {
    List<PsiFile> files = externalAnnotationsManager.findExternalAnnotationsFiles(owner);
    if (files != null) {
      List<PsiFile> elements = new ArrayList<>(files);
      elements.add(file);
      return elements;
    }
    return Collections.singletonList(file);
  }

  @NotNull
  private PsiAnnotation[] getAnnotations(@NotNull Project project, PsiModifierListOwner owner) {
    PsiAnnotation[] annotations = ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(owner);
    if (annotations == null) {
      return PsiAnnotation.EMPTY_ARRAY;
    }
    else {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      return Arrays.stream(annotations).filter(anno -> {
        String qualifiedName = anno.getQualifiedName();
        return qualifiedName != null &&
               facade.findClass(qualifiedName, owner.getResolveScope()) != null &&
               !owner.hasAnnotation(qualifiedName);
      }).toArray(PsiAnnotation[]::new);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static boolean isWritable(PsiModifierListOwner owner) {
    if (owner instanceof PsiCompiledElement) return false;

    VirtualFile vFile = PsiUtilCore.getVirtualFile(owner);
    return vFile != null && vFile.isInLocalFileSystem();
  }
}
