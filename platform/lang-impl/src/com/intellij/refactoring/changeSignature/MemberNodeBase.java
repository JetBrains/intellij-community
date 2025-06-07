// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.tree.TreeNode;
import java.util.*;

public abstract class MemberNodeBase<M extends PsiElement> extends CheckedTreeNode {
  protected final M myMethod;
  protected final Set<M> myCalled;
  protected final Project myProject;
  protected final Runnable myCancelCallback;
  private boolean myOldChecked;

  protected abstract MemberNodeBase<M> createNode(M caller, HashSet<M> called);

  protected abstract @Unmodifiable List<M> computeCallers();

  protected abstract void customizeRendererText(ColoredTreeCellRenderer renderer);

  protected Condition<M> getFilter() {
    return Conditions.alwaysTrue();
  } 

  protected MemberNodeBase(final M method, Set<M> called, Project project, Runnable cancelCallback) {
    super(method);
    myMethod = method;
    myCalled = called;
    myProject = project;
    myCancelCallback = cancelCallback;
    isChecked = false;
  }

  //IMPORTANT: do not build children in children()
  private void buildChildren() {
    if (children == null) {
      final List<M> callers = findCallers();
      //noinspection UseOfObsoleteCollectionType
      children = new Vector<>(callers.size());
      for (M caller : callers) {
        final HashSet<M> called = new HashSet<>(myCalled);
        called.add(getMember());
        final MemberNodeBase<M> child = createNode(caller, called);
        children.add(child);
        child.parent = this;
      }
    }
  }

  @Override
  public TreeNode getChildAt(int index) {
    buildChildren();
    return super.getChildAt(index);
  }

  @Override
  public int getChildCount() {
    buildChildren();
    return super.getChildCount();
  }

  @Override
  public boolean isLeaf() {
    if (children == null) {
      return false;
    }
    return super.isLeaf();
  }

  @Override
  public int getIndex(TreeNode aChild) {
    buildChildren();
    return super.getIndex(aChild);
  }

  private List<M> findCallers() {
    if (getMember() == null) return Collections.emptyList();
    final Ref<List<M>> callers = new Ref<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> callers.set(ContainerUtil.filter(computeCallers(), getFilter()))), RefactoringBundle.message("caller.chooser.looking.for.callers"), true, myProject)) {
      myCancelCallback.run();
      return Collections.emptyList();
    }
    return callers.get();
  }

  public void customizeRenderer(ColoredTreeCellRenderer renderer) {
    if (getMember() == null) return;
    final int flags = Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS;
    renderer.setIcon(ReadAction.compute(() -> getMember().getIcon(flags)));

    customizeRendererText(renderer);
  }

  @Override
  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);
    if (!enabled) {
      myOldChecked = isChecked();
      setChecked(false);
    }
    else {
      setChecked(myOldChecked);
    }
  }

  public M getMember() {
    return myMethod;
  }

  public PsiElement getElementToSearch() {
    return getMember();
  }

}
