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

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    return new ArrayList<AbstractTreeNode>(Collections.singleton(myFileNode));
  }

  @Override
  public void update(PresentationData presentation) {
  }

  public Object getFileNode() {
    return myFileNode;
  }

  @Override
  public int getFileCount(final PsiFile val) {
    return 1;
  }

  @Override
  public int getTodoItemCount(final PsiFile val) {
    return getTreeStructure().getTodoItemCount(val);
  }
}
