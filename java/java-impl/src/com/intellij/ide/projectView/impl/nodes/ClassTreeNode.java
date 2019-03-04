// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class ClassTreeNode extends BasePsiMemberNode<PsiClass> {

  private final Collection<? extends AbstractTreeNode> myMandatoryChildren;

  public ClassTreeNode(Project project, @NotNull PsiClass value, ViewSettings viewSettings) {
    this(project, value, viewSettings, ContainerUtil.emptyList());
  }

  public ClassTreeNode(Project project,
                       @NotNull PsiClass value,
                       ViewSettings viewSettings,
                       @NotNull Collection<? extends AbstractTreeNode> mandatoryChildren) {
    super(project, value, viewSettings);
    myMandatoryChildren = mandatoryChildren;
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    PsiClass parent = getValue();
    final ArrayList<AbstractTreeNode> treeNodes = new ArrayList<>(myMandatoryChildren);

    if (getSettings().isShowMembers()) {
      ArrayList<PsiElement> result = new ArrayList<>();
      try {
        PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(parent, result);
      }
      catch (IndexNotReadyException ignore) {
      }
      for (PsiElement psiElement : result) {
        if (!psiElement.isPhysical()) {
          continue;
        }

        if (psiElement instanceof PsiClass) {
          treeNodes.add(new ClassTreeNode(getProject(), (PsiClass)psiElement, getSettings()));
        }
        else if (psiElement instanceof PsiMethod) {
          treeNodes.add(new PsiMethodNode(getProject(), (PsiMethod)psiElement, getSettings()));
        }
        else if (psiElement instanceof PsiField) {
          treeNodes.add(new PsiFieldNode(getProject(), (PsiField)psiElement, getSettings()));
        }
      }
    }
    return treeNodes;
  }

  @Override
  public boolean isAlwaysLeaf() {
    return !getSettings().isShowMembers() && myMandatoryChildren.isEmpty();
  }

  @Override
  public void updateImpl(@NotNull PresentationData data) {
    final PsiClass aClass = getValue();
    if (aClass != null) {
      data.setPresentableText(aClass.getName());
    }
  }

  public boolean isTopLevel() {
    return getValue() != null && getValue().getParent()instanceof PsiFile;
  }


  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  public PsiClass getPsiClass() {
    return getValue();
  }

  @Override
  public boolean isAlwaysExpand() {
    return getParentValue() instanceof PsiFile;
  }

  @Override
  public int getWeight() {
    return 20;
  }

  @Override
  public String getTitle() {
    final PsiClass psiClass = getValue();
    if (psiClass != null && psiClass.isValid()) {
      return psiClass.getQualifiedName();
    }
    return super.getTitle();
  }

  @Override
  protected boolean isMarkReadOnly() {
    return true;
  }

  @Override
  public int getTypeSortWeight(final boolean sortByType) {
    return sortByType ? 5 : 0;
  }

  @Override
  public Comparable getTypeSortKey() {
    return new ClassNameSortKey();
  }

  public static int getClassPosition(final PsiClass aClass) {
    if (aClass == null || !aClass.isValid()) {
      return 0;
    }
    try {
      int pos = aClass instanceof JspClass ? ElementPresentationUtil.CLASS_KIND_JSP : ElementPresentationUtil.getClassKind(aClass);
      //abstract class before concrete
      if (pos == ElementPresentationUtil.CLASS_KIND_CLASS || pos == ElementPresentationUtil.CLASS_KIND_EXCEPTION) {
        boolean isAbstract = aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !aClass.isInterface();
        if (isAbstract) {
          pos --;
        }
      }
      return pos;
    }
    catch (IndexNotReadyException e) {
      return 0;
    }
  }

  private class ClassNameSortKey implements Comparable {
    @Override
    public int compareTo(final Object o) {
      if (!(o instanceof ClassNameSortKey)) return 0;
      ClassNameSortKey rhs = (ClassNameSortKey) o;
      return getPosition() - rhs.getPosition();
    }

    int getPosition() {
      return getClassPosition(getValue());
    }
  }

  @Override
  public boolean shouldDrillDownOnEmptyElement() {
    return true;
  }

  @Override
  public boolean canRepresent(final Object element) {
    if (!isValid()) return false;

    return super.canRepresent(element) || canRepresent(getValue(), element);
  }

  private boolean canRepresent(final PsiClass psiClass, final Object element) {
    if (psiClass == null || !psiClass.isValid() || element == null) return false;

    final PsiFile parentFile = parentFileOf(psiClass);
    if (parentFile != null && (parentFile == element || element.equals(parentFile.getVirtualFile()))) return true;

    if (!getSettings().isShowMembers()) {
      if (element instanceof PsiElement && ((PsiElement)element).isValid()) {
        PsiFile elementFile = ((PsiElement)element).getContainingFile();
        if (elementFile != null && parentFile != null) {
          return elementFile.equals(parentFile);
        }
      }
    }

    return false;
  }

  @Nullable
  private static PsiFile parentFileOf(final PsiClass psiClass) {
    return psiClass.getContainingClass() == null ? psiClass.getContainingFile() : null;
  }
}
