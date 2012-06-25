/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Danila Ponomarenko
 */
public class ChangeClassSignatureFromUsageFix extends BaseIntentionAction {
  private final PsiClass myClass;
  private final PsiReferenceParameterList myParameterList;

  public ChangeClassSignatureFromUsageFix(@NotNull PsiClass aClass,
                                          @NotNull PsiReferenceParameterList parameterList) {
    myClass = aClass;
    myParameterList = parameterList;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("change.class.signature.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myClass.isValid() || !myParameterList.isValid()) {
      return false;
    }

    if (myClass.getTypeParameters().length >= myParameterList.getTypeArguments().length) {
      return false;
    }

    final PsiTypeParameterList classTypeParameterList = myClass.getTypeParameterList();
    if (classTypeParameterList == null) {
      return false;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    Map<PsiTypeParameter, Boolean> typeParameterBooleanMap = createTypeParameters(
      factory,
      classTypeParameterList.getTypeParameters(),
      myParameterList.getTypeParameterElements()
    );

    if (isAddOneTypeParameter(typeParameterBooleanMap)) {
      setText(QuickFixBundle.message("add.type.parameter.text", myClass.getName()));
    }
    else {
      setText(QuickFixBundle.message("change.class.signature.text", myClass.getName(), parametersToSignatureText(typeParameterBooleanMap)));
    }

    return true;
  }

  private static boolean isAddOneTypeParameter(@NotNull Map<PsiTypeParameter, Boolean> map) {
    boolean oneParameter = false;
    for (Boolean b : map.values()) {
      if (b == Boolean.TRUE) {
        if (oneParameter) {
          return false;
        }
        oneParameter = true;
      }
    }
    return true;
  }

  @NotNull
  private static String parametersToSignatureText(@NotNull Map<PsiTypeParameter, Boolean> map) {
    final StringBuilder result = new StringBuilder("&lt;");
    for (Map.Entry<PsiTypeParameter, Boolean> e : map.entrySet()) {
      final String text = e.getKey().getText();
      if (e.getValue() == Boolean.TRUE) {
        result.append("<b>").append(text).append("</b>");
      }
      else {
        result.append(text);
      }
      result.append(", ");
    }

    final int lng = result.length();
    result.delete(lng - 2, lng);

    return result.append("&gt;").toString();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    final PsiTypeParameterList classTypeParameterList = myClass.getTypeParameterList();
    if (classTypeParameterList == null) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    final PsiElement newTypeParameterList = classTypeParameterList.replace(
      createTypeParameterList(
        factory,
        classTypeParameterList.getTypeParameters(),
        myParameterList.getTypeParameterElements()
      )
    );

    navigateTo(newTypeParameterList);
  }

  private static void navigateTo(@NotNull PsiElement element) {
    element.getContainingFile().navigate(false);
    final Editor editor = PsiUtilBase.findEditor(element);
    if (editor == null) {
      return;
    }

    editor.getCaretModel().moveToOffset(element.getTextRange().getStartOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }

  @NotNull
  private static PsiTypeParameterList createTypeParameterList(@NotNull PsiElementFactory factory,
                                                              @NotNull PsiTypeParameter[] classTypeParameters,
                                                              @NotNull PsiTypeElement[] typeElements) {
    final PsiTypeParameterList result = factory.createTypeParameterList();
    for (PsiTypeParameter p : createTypeParameters(factory, classTypeParameters, typeElements).keySet()) {
      result.add(p);
    }
    return result;
  }

  @NotNull
  private static Map<PsiTypeParameter, Boolean> createTypeParameters(@NotNull PsiElementFactory factory,
                                                                     @NotNull PsiTypeParameter[] classTypeParameters,
                                                                     @NotNull PsiTypeElement[] typeElements) {
    final LinkedHashMap<PsiTypeParameter, Boolean> result = new LinkedHashMap<PsiTypeParameter, Boolean>();
    final TypeParameterNameSuggester suggester = new TypeParameterNameSuggester(classTypeParameters);

    final Queue<PsiTypeParameter> classTypeParametersQueue = new LinkedList<PsiTypeParameter>(Arrays.asList(classTypeParameters));
    for (PsiTypeElement typeElement : typeElements) {
      if (!classTypeParametersQueue.isEmpty()) {
        final PsiTypeParameter typeParameter = classTypeParametersQueue.peek();

        if (isAssignable(typeParameter, typeElement.getType())) {
          result.put(typeParameter, false);
          classTypeParametersQueue.poll();
          continue;
        }
      }
      result.put(toTypeParameter(factory, suggester, typeElement), true);
    }
    return result;
  }

  private static boolean isAssignable(@NotNull PsiTypeParameter typeParameter, @NotNull PsiType type) {
    for (PsiClassType t : typeParameter.getExtendsListTypes()) {
      if (!t.isAssignableFrom(type)) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  private static PsiTypeParameter toTypeParameter(@NotNull PsiElementFactory factory,
                                                  @NotNull TypeParameterNameSuggester suggester,
                                                  @NotNull PsiTypeElement typeElement) {
    final PsiType type = typeElement.getType();

    return factory.createTypeParameter(suggester.suggest((PsiClassType)type), PsiClassType.EMPTY_ARRAY);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }


  private static class TypeParameterNameSuggester {
    private final Set<String> usedNames = new HashSet<String>();

    public TypeParameterNameSuggester(@NotNull PsiTypeParameter[] typeParameters) {
      for (PsiTypeParameter p : typeParameters) {
        usedNames.add(p.getName());
      }
    }

    @NotNull
    private String suggestUnusedName(@NotNull String name) {
      String unusedName = name;
      int i = 0;
      while (true) {
        if (usedNames.add(unusedName)) {
          return unusedName;
        }
        unusedName = name + ++i;
      }
    }

    @NotNull
    public String suggest(@NotNull PsiClassType type) {
      return suggestUnusedName(type.getClassName().substring(0, 1).toUpperCase());
    }
  }
}
