package com.intellij.ide.todo.nodes;

import com.intellij.ide.todo.ToDoSettings;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class BaseToDoNode<Value> extends AbstractTreeNode<Value> {
  protected final ToDoSettings myToDoSettings;
  protected final TodoTreeBuilder myBuilder;

  protected BaseToDoNode(Project project, Value value, TodoTreeBuilder builder) {
    super(project, value);
    myBuilder = builder;
    myToDoSettings = myBuilder.getTodoTreeStructure();
  }

  public boolean contains(VirtualFile file) {
    return false;
  }

  protected TodoTreeStructure getTreeStructure() {
    return myBuilder.getTodoTreeStructure();
  }

  public abstract int getFileCount(Value val);

  public abstract int getTodoItemCount(Value val);
}
