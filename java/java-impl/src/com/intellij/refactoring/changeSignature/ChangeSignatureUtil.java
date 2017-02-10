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
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class ChangeSignatureUtil {
  private ChangeSignatureUtil() { }

  public interface ChildrenGenerator<Parent extends PsiElement, Child extends PsiElement> {
    List<Child> getChildren(Parent parent);
  }

  public static <Parent extends PsiElement, Child extends PsiElement> void synchronizeList(
    Parent list,
    List<Child> newElements,
    ChildrenGenerator<Parent, Child> generator,
    boolean[] shouldRemoveChild) throws IncorrectOperationException
  {
    List<Child> elementsToRemove = null;
    List<Child> elements;

    int index = 0;
    while (true) {
      elements = generator.getChildren(list);
      if (index == newElements.size()) break;

      if (elementsToRemove == null) {
        elementsToRemove = new ArrayList<>();
        for (int i = 0; i < shouldRemoveChild.length; i++) {
          if (shouldRemoveChild[i] && i < elements.size()) {
            elementsToRemove.add(elements.get(i));
          }
        }
      }

      Child oldElement = index < elements.size() ? elements.get(index) : null;
      Child newElement = newElements.get(index);
      if (newElement != null) {
        if (!newElement.equals(oldElement)) {
          if (oldElement != null && elementsToRemove.contains(oldElement)) {
            oldElement.delete();
            index--;
          }
          else {
            assert list.isWritable() : PsiUtilCore.getVirtualFile(list);
            list.addBefore(newElement, oldElement);
            if (list.equals(newElement.getParent())) {
              newElement.delete();
            }
          }
        }
      }
      else {
        if (newElements.size() > 1 && (!elements.isEmpty() || index < newElements.size() - 1)) {
          PsiElement anchor;
          if (index == 0) {
            anchor = list.getFirstChild();
          }
          else {
            anchor = index - 1 < elements.size() ? elements.get(index - 1) : null;
          }
          CharTable charTable = SharedImplUtil.findCharTableByTree(list.getNode());
          PsiElement psi = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, charTable, list.getManager()).getPsi();
          if (anchor != null) {
            list.addAfter(psi, anchor);
          }
          else {
            list.add(psi);
          }
        }
      }
      index++;
    }

    for (int i = newElements.size(); i < elements.size(); i++) {
      Child element = elements.get(i);
      element.delete();
    }
  }

  public static void invokeChangeSignatureOn(PsiMethod method, Project project) {
    ChangeSignatureHandler handler = LanguageRefactoringSupport.INSTANCE.forLanguage(method.getLanguage()).getChangeSignatureHandler();
    if (handler != null) {
      handler.invoke(project, new PsiElement[]{method}, null);
    }
  }

  public static boolean deepTypeEqual(PsiType type1, PsiType type2) {
    if (type1 == type2) return true;
    if (type1 == null || !type1.equals(type2)) return false;
    return Comparing.equal(type1.getCanonicalText(true), type2.getCanonicalText(true));
  }
}
