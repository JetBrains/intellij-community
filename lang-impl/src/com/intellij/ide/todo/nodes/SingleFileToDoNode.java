package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class SingleFileToDoNode extends BaseToDoNode<PsiFile>{
  private final TodoFileNode myFileNode = new TodoFileNode(getProject(), getValue(), myBuilder, true);

  public SingleFileToDoNode(Project project, PsiFile value, TodoTreeBuilder builder) {
    super(project, value, builder);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    return new ArrayList<AbstractTreeNode>(Collections.singleton(myFileNode));
  }

  public void update(PresentationData presentation) {
  }

  public Object getFileNode() {
    return myFileNode;
  }

  public int getFileCount(final PsiFile val) {
    return 1;
  }

  public int getTodoItemCount(final PsiFile val) {
    return getTreeStructure().getTodoItemCount(val);
  }
}
