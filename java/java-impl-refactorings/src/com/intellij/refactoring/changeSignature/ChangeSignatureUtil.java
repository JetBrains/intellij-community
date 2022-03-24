// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CharTable;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public final class ChangeSignatureUtil {
  private ChangeSignatureUtil() { }

  public interface ChildrenGenerator<Parent extends PsiElement, Child extends PsiElement> {
    List<Child> getChildren(Parent parent);
  }

  public static <Parent extends PsiElement, Child extends PsiElement> void synchronizeList(
    Parent list,
    List<? extends Child> newElements,
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
    RefactoringSupportProvider provider = LanguageRefactoringSupport.INSTANCE.forContext(method);
    ChangeSignatureHandler handler = provider != null ? provider.getChangeSignatureHandler() : null;
    if (handler != null) {
      handler.invoke(project, new PsiElement[]{method}, null);
    }
  }

  /**
   * @deprecated use CommonJavaRefactoringUtil.deepTypeEqual instead
   */
  @Deprecated
  public static boolean deepTypeEqual(PsiType type1, PsiType type2) {
    return CommonJavaRefactoringUtil.deepTypeEqual(type1, type2);
  }
}
