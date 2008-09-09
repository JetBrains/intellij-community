package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class HierarchyTreeStructure extends AbstractTreeStructure {
  protected HierarchyNodeDescriptor myBaseDescriptor;
  private HierarchyNodeDescriptor myRoot;
  protected final Project myProject;

  protected HierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor baseDescriptor) {
    myBaseDescriptor = baseDescriptor;
    myProject = project;
    myRoot = myBaseDescriptor;
  }

  public final HierarchyNodeDescriptor getBaseDescriptor() {
    return myBaseDescriptor;
  }

  protected final void setBaseElement(final HierarchyNodeDescriptor baseElement) {
    myBaseDescriptor = baseElement;
    myRoot = myBaseDescriptor;
    while(myRoot.getParentDescriptor() != null){
      myRoot = (HierarchyNodeDescriptor)myRoot.getParentDescriptor();
    }
  }

  @NotNull
  public final NodeDescriptor createDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
    if (element instanceof HierarchyNodeDescriptor) {
      return (HierarchyNodeDescriptor)element;
    }
    else if (element instanceof String) {
      return new TextInfoNodeDescriptor(parentDescriptor, (String)element, myProject);
    }
    else {
      return null;
    }
  }

  public final boolean isToBuildChildrenInBackground(final Object element) {
    if (element instanceof HierarchyNodeDescriptor){
      final HierarchyNodeDescriptor descriptor = (HierarchyNodeDescriptor)element;
      final Object[] cachedChildren = descriptor.getCachedChildren();
      if (cachedChildren == null && descriptor.isValid()){
        return true;
      }
    }
    return false;
  }

  public final Object[] getChildElements(final Object element) {
    if (element instanceof HierarchyNodeDescriptor) {
      final HierarchyNodeDescriptor descriptor = (HierarchyNodeDescriptor)element;
      final Object[] cachedChildren = descriptor.getCachedChildren();
      if (cachedChildren == null) {
        if (!descriptor.isValid()){ //invalid
          descriptor.setCachedChildren(ArrayUtil.EMPTY_OBJECT_ARRAY);
        }
        else{
          descriptor.setCachedChildren(buildChildren(descriptor));
        }
      }
      else{
/*
        for(int i = 0; i < cachedChildren.length; i++){
          Object child = cachedChildren[i];
          if (child instanceof HierarchyElement){
            if (!((HierarchyElement)child).getValue().isValid()){
              hierarchyElement.setCachedChildren(new Object[0]); //?
              break;
            }
          }
        }
*/
      }
      return descriptor.getCachedChildren();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public final Object getParentElement(final Object element) {
    if (element instanceof HierarchyNodeDescriptor) {
      return ((HierarchyNodeDescriptor)element).getParentDescriptor();
    }

    return null;
  }

  public final void commit() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
  }

  public final boolean hasSomethingToCommit() {
    return PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments();
  }

  protected abstract Object[] buildChildren(HierarchyNodeDescriptor descriptor);

  public final Object getRootElement() {
    return myRoot;
  }

  private static final class TextInfoNodeDescriptor extends NodeDescriptor {
    public TextInfoNodeDescriptor(final NodeDescriptor parentDescriptor, final String text, final Project project) {
      super(project, parentDescriptor);
      myName = text;
      myColor = Color.red;
    }

    public final Object getElement() {
      return myName;
    }

    public final boolean update() {
      return true;
    }
  }

  public boolean isAlwaysShowPlus() {
    return false;
  }
}
