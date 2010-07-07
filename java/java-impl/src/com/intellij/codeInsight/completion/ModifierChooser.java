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
package com.intellij.codeInsight.completion;

import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.classes.InterfaceFilter;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 17.02.2003
 * Time: 17:03:09
 * To change this template use Options | File Templates.
 */

@SuppressWarnings({"HardCodedStringLiteral"})
public class ModifierChooser
 implements KeywordChooser{
  private static final Map<ElementFilter, String[][]> myMap = new HashMap<ElementFilter, String[][]>();

  static {
    myMap.put(new NotFilter(new InterfaceFilter()), new String[][]{
      new String[]{"public", "protected", "private"},
      new String[]{"static"},
      new String[]{"final", "abstract"},
      new String[]{"native"},
      new String[]{"synchronized"},
      new String[]{"strictfp"},
      new String[]{"volatile"},
      new String[]{"transient"}
    });

    myMap.put(new InterfaceFilter(), new String[][]{
      new String[]{"public", "protected"},
      new String[]{"static"},
      new String[]{"final"}
    });


    myMap.put(new ClassFilter(PsiJavaFile.class), new String[][]{
      new String[]{"public"},
      new String[]{"final", "abstract"}
    });

    myMap.put(new OrFilter(new ClassFilter(PsiStatement.class), new ClassFilter(PsiCodeBlock.class)), new String[][]{
      new String[]{"final", "synchronized"}
    });

    myMap.put(new ClassFilter(PsiParameterList.class), new String[][]{
      new String[]{"final"}
    });
  }

  public String[] getKeywords(CompletionContext context, PsiElement position){
    final List<String> ret = new ArrayList<String>();
    try{
      PsiElement scope;

      if(position == null)
        scope = context.file;
      else
        scope = position.getParent();

      final PsiModifierList list = getModifierList(position);

scopes:
      while (scope != null) {
        for (final Object o : myMap.keySet()) {
          final ElementFilter filter = (ElementFilter)o;
          if (filter.isClassAcceptable(scope.getClass()) && filter.isAcceptable(scope, scope.getParent())) {
            final String[][] keywordSets = myMap.get(filter);
            for (int i = 0; i < keywordSets.length; i++) {
              final String[] keywords = keywordSets[keywordSets.length - i - 1];
              boolean containModifierFlag = false;
              if (list != null) {
                for (@Modifier String keyword : keywords) {
                  if (list.hasModifierProperty(keyword)) {
                    containModifierFlag = true;
                    break;
                  }
                }
              }
              if (!containModifierFlag) {
                ContainerUtil.addAll(ret, keywords);
              }
            }
            break scopes;
          }
        }
        scope = scope.getParent();
        if (scope instanceof JspClassLevelDeclarationStatement) {
          scope = scope.getContext();
        }
        if (scope instanceof PsiDirectory) break;
      }
    }
    catch(Exception e){}
    return ArrayUtil.toStringArray(ret);
  }

  private static PsiModifierList getModifierList(PsiElement element)
  throws Exception{
    if(element == null){
      return null;
    }
    if(element.getParent() instanceof PsiModifierList)
      return (PsiModifierList)element.getParent();

    final PsiElement prev = FilterPositionUtil.searchNonSpaceNonCommentBack(element);

    if(prev != null) {
      final PsiModifierList modifierList = PsiTreeUtil.getParentOfType(prev, PsiModifierList.class);
      if(modifierList != null){
        return modifierList;
      }
    }

    PsiElement parent = element.getParent();
    while(parent != null && (parent instanceof PsiJavaCodeReferenceElement
      || parent instanceof PsiErrorElement || parent instanceof PsiTypeElement
      || parent instanceof PsiMethod || parent instanceof PsiVariable
      || parent instanceof PsiDeclarationStatement || parent instanceof PsiImportList
      || parent instanceof PsiDocComment
      || element.getText().equals(parent.getText()))){
      parent = parent.getParent();
      if (parent instanceof JspClassLevelDeclarationStatement) {
        parent = parent.getContext();
      }
    }

    if(parent == null) throw new Exception();
    for (final Object o : myMap.keySet()) {
      final ElementFilter filter = (ElementFilter)o;
      if (filter.isClassAcceptable(parent.getClass()) && filter.isAcceptable(parent, parent.getParent())) {
        if (parent instanceof PsiParameterList) {
          if (prev == null || Arrays.asList(new String[]{"(", ","}).contains(prev.getText())
              || Arrays.asList(new String[]{"(", ","}).contains(element.getText())) {
            return null;
          }
        }
        else if (prev == null || JavaCompletionData.END_OF_BLOCK.isAcceptable(element, prev.getParent())) {
          return null;
        }
      }
    }

    throw new Exception("Can't find modifier list");
  }

  public String toString(){
    return "modifier-chooser";
  }
}
