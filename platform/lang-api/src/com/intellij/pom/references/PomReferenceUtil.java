/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.pom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.DelegatePsiTarget;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaDataTarget;
import com.intellij.pom.PomTarget;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PomReferenceUtil {

  public static String getReferenceText(PomReference reference) {
    return reference.getRangeInElement().substring(reference.getElement().getText());
  }

  public static TextRange getDefaultRangeInElement(PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    assert manipulator != null: "Cannot find manipulator for " + element;
    return manipulator.getRangeInElement(element);
  }

  public static void changeContent(PomReference reference, String newContent) {
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(reference.getElement());
    assert manipulator != null: "Cannot find manipulator for " + reference.getElement();
    manipulator.handleContentChange(reference.getElement(), reference.getRangeInElement(), newContent);
  }


  @NotNull
  public static PomTarget convertPsi2Target(@NotNull PsiElement element) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaOwner metaOwner = (PsiMetaOwner)element;
      final PsiMetaData psiMetaData = metaOwner.getMetaData();
      if (psiMetaData != null) {
        return new PsiMetaDataTarget(psiMetaData);
      }
    }
    if (element instanceof PomTarget) {
      return (PomTarget)element;
    }
    return new DelegatePsiTarget(element);
  }
}
