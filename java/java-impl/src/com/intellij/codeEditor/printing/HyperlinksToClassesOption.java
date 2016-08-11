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

/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.codeEditor.printing;

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

public class HyperlinksToClassesOption extends PrintOption {
  private JCheckBox myCbGenerateHyperlinksToClasses;
  private boolean isGenerateHyperlinksToClasses;

  @Nullable
  public TreeMap<Integer, PsiReference> collectReferences(PsiFile psiFile, Map<PsiFile, PsiFile> filesMap) {
    if (isGenerateHyperlinksToClasses) {
      FileType fileType = psiFile.getFileType();
      if (StdFileTypes.JAVA == fileType || StdFileTypes.JSP == fileType) {
        final TreeMap<Integer, PsiReference> refMap = new TreeMap<>();
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


  private static void findClassReferences(PsiElement psiElement, TreeMap<Integer, PsiReference> refMap, Map<PsiFile, PsiFile> filesMap, PsiFile psiFile) {
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
    public JComponent createComponent() {
      myCbGenerateHyperlinksToClasses = new JCheckBox(CodeEditorBundle.message("export.to.html.generate.hyperlinks.checkbox"), isGenerateHyperlinksToClasses);
      return myCbGenerateHyperlinksToClasses;
    }

    public boolean isModified() {
      return myCbGenerateHyperlinksToClasses.isSelected() != isGenerateHyperlinksToClasses;
    }

    public void apply() throws ConfigurationException {
      isGenerateHyperlinksToClasses = myCbGenerateHyperlinksToClasses.isSelected();
    }

    public void reset() {
      myCbGenerateHyperlinksToClasses.setSelected(isGenerateHyperlinksToClasses);
    }

    public void disposeUIResources() {
    }
  }
}