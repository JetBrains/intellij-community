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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public class PsiMethodTreeElement extends JavaClassTreeElementBase<PsiMethod> implements SortableTreeElement {
  public PsiMethodTreeElement(PsiMethod method, boolean isInherited) {
    super(isInherited, method);
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    final ArrayList<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
    final PsiMethod element = getElement();
    if (element == null || element instanceof JspHolderMethod) return result;

    final TextRange range = element.getTextRange();
    if (range == null) return result;

    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null || psiFile instanceof PsiCompiledElement) return result;

    final String fileText = psiFile.getText();
    if (fileText == null) return result;
    
    if (!range.substring(fileText).contains(PsiKeyword.CLASS)) return result;

    element.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override public void visitClass(PsiClass aClass) {
        if (!(aClass instanceof PsiAnonymousClass) && !(aClass instanceof PsiTypeParameter)) {
          result.add(new JavaClassTreeElement(aClass, isInherited(), new HashSet<PsiClass>(Arrays.asList(aClass.getSupers()))));
        }

      }
    });
    return result;
  }

  public String getPresentableText() {
    return PsiFormatUtil.formatMethod(
      getElement(),
      PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME |
                            PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_PARAMETERS,
      PsiFormatUtilBase.SHOW_TYPE
    );
  }


  @Override
  public TextAttributesKey getTextAttributesKey() {
    if (isInherited()) return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
    return super.getTextAttributesKey();
  }

  public PsiMethod getMethod() {
    return getElement();
  }

  public String getAlphaSortKey() {
    final PsiMethod method = getElement();
    if (method != null) {
      return method.getName() + " " + StringUtil.join(method.getParameterList().getParameters(), new Function<PsiParameter, String>() {
        @Override
        public String fun(PsiParameter psiParameter) {
          PsiTypeElement typeElement = psiParameter.getTypeElement();
          return typeElement != null ? typeElement.getText() : "";
        }
      }, " ");
    }
    return "";
  }
}
