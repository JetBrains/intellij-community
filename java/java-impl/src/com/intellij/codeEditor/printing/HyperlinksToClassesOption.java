/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeEditor.printing;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.TreeMap;

public final class HyperlinksToClassesOption extends PrintOption {
  private JCheckBox myCbGenerateHyperlinksToClasses;
  private boolean isGenerateHyperlinksToClasses;

  @Override
  @Nullable
  public Map<Integer, PsiReference> collectReferences(@NotNull PsiFile psiFile, @NotNull Map<PsiFile, PsiFile> filesMap) {
    if (isGenerateHyperlinksToClasses) {
      FileType fileType = psiFile.getFileType();
      if (JavaFileType.INSTANCE == fileType || StdFileTypes.JSP == fileType) {
        Map<Integer, PsiReference> refMap = new TreeMap<>();
        findClassReferences(psiFile, refMap, filesMap, psiFile);
        return refMap;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public UnnamedConfigurable createConfigurable() {
    return new HyperlinksToClassesConfigurable();
  }


  private static void findClassReferences(@NotNull PsiElement psiElement, @NotNull Map<? super Integer, ? super PsiReference> refMap, @NotNull Map<PsiFile, PsiFile> filesMap, @NotNull PsiFile psiFile) {
    PsiReference ref = psiElement.getReference();
    if(ref instanceof PsiJavaCodeReferenceElement) {
      PsiElement refElement = ref.resolve();
      if(refElement instanceof PsiClass) {
        PsiFile containingFile = refElement.getContainingFile();
        if(!containingFile.equals(psiFile) && filesMap.get(containingFile) != null) {
          refMap.put(psiElement.getTextRange().getStartOffset(), ref);
        }
        return;
      }
    }
    PsiElement[] children = psiElement.getChildren();
    for (PsiElement aChildren : children) {
      findClassReferences(aChildren, refMap, filesMap, psiFile);
    }
  }

  private class HyperlinksToClassesConfigurable implements UnnamedConfigurable {
    @Override
    public JComponent createComponent() {
      myCbGenerateHyperlinksToClasses = new JCheckBox(JavaBundle.message("export.to.html.generate.hyperlinks.checkbox"), isGenerateHyperlinksToClasses);
      return myCbGenerateHyperlinksToClasses;
    }

    @Override
    public boolean isModified() {
      return myCbGenerateHyperlinksToClasses.isSelected() != isGenerateHyperlinksToClasses;
    }

    @Override
    public void apply() throws ConfigurationException {
      isGenerateHyperlinksToClasses = myCbGenerateHyperlinksToClasses.isSelected();
    }

    @Override
    public void reset() {
      myCbGenerateHyperlinksToClasses.setSelected(isGenerateHyperlinksToClasses);
    }
  }
}