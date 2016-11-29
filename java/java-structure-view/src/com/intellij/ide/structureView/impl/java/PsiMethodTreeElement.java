/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.psi.util.PsiFormatUtilBase.*;

public class PsiMethodTreeElement extends JavaClassTreeElementBase<PsiMethod> implements SortableTreeElement {

  public PsiMethodTreeElement(PsiMethod method, boolean isInherited) {
    super(isInherited, method);
  }

  @Override
  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    final List<StructureViewTreeElement> emptyResult = Collections.emptyList();
    final PsiMethod element = getElement();
    if (element == null || element instanceof SyntheticElement) return emptyResult;

    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null || psiFile instanceof PsiCompiledElement) return emptyResult;

    final TextRange range = element.getTextRange();
    if (range == null) return emptyResult;

    final String fileText = psiFile.getText();
    if (fileText == null) return emptyResult;

    if (!range.substring(fileText).contains(PsiKeyword.CLASS)) return emptyResult;

    final ArrayList<StructureViewTreeElement> result = new ArrayList<>();

    element.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override public void visitClass(PsiClass aClass) {
        if (!(aClass instanceof PsiAnonymousClass) && !(aClass instanceof PsiTypeParameter)) {
          result.add(new JavaClassTreeElement(aClass, isInherited(), new HashSet<>(Arrays.asList(aClass.getSupers()))));
        }
      }
    });
    return result;
  }

  @Override
  public String getPresentableText() {
    final PsiMethod psiMethod = getElement();
    if (psiMethod == null) return "";
    final boolean dumb = DumbService.isDumb(psiMethod.getProject());
    String method = PsiFormatUtil.formatMethod(psiMethod, 
                                               PsiSubstitutor.EMPTY,
                                               SHOW_NAME | TYPE_AFTER | SHOW_PARAMETERS | (dumb ? 0 : SHOW_TYPE), 
                                               dumb ? SHOW_NAME : SHOW_TYPE);
    return StringUtil.replace(method, ":", ": ");
  }


  @Override
  public String getLocationString() {
    if (!Registry.is("show.method.base.class.in.java.file.structure")) return null;
    final PsiMethod method = getElement();
    if (myLocation == null && method != null && !DumbService.isDumb(method.getProject())) {
      if (isInherited()) {
        return super.getLocationString();
      } else {
      try {
        final MethodSignatureBackedByPsiMethod baseMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
        if (baseMethod != null && !method.isEquivalentTo(baseMethod.getMethod())) {
          PsiMethod base = baseMethod.getMethod();
          PsiClass baseClass = base.getContainingClass();
          if (baseClass != null /*&& !CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName())*/) {
            if (baseClass.getMethods().length > 1) {
              myLocation = baseClass.getName();
            }
          }
        }
      }
      catch (IndexNotReadyException e) {
        //some searchers (EJB) require indices. What shall we do?
      }

      if (StringUtil.isEmpty(myLocation)) {
        myLocation = "";
      } else {
        char upArrow = '\u2191';
        myLocation = UIUtil.getLabelFont().canDisplay(upArrow) ? upArrow + myLocation : myLocation;
      }
      }
    }
    return StringUtil.isEmpty(myLocation) ? null : myLocation;
  }

  @Override
  public TextAttributesKey getTextAttributesKey() {
    if (isInherited()) return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
    return super.getTextAttributesKey();
  }

  public PsiMethod getMethod() {
    return getElement();
  }

  @NotNull
  @Override
  public String getAlphaSortKey() {
    final PsiMethod method = getElement();
    if (method != null) {
      return method.getName() + " " + StringUtil.join(method.getParameterList().getParameters(), psiParameter -> {
        PsiTypeElement typeElement = psiParameter.getTypeElement();
        return typeElement != null ? typeElement.getText() : "";
      }, " ");
    }
    return "";
  }

  @Override
  public String getLocationPrefix() {
    return " ";
  }

  @Override
  public String getLocationSuffix() {
    return "";
  }
}
