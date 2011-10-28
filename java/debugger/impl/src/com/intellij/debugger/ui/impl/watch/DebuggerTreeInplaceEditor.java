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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.ui.tree.TreeInplaceEditor;

import javax.swing.*;
import javax.swing.tree.TreePath;

public abstract class DebuggerTreeInplaceEditor extends TreeInplaceEditor {
  private final DebuggerTreeNodeImpl myNode;

  protected Project getProject() {
    return myNode.getTree().getProject();
  }

  public DebuggerTreeInplaceEditor(DebuggerTreeNodeImpl node) {
    myNode = node;
  }

  protected TreePath getNodePath() {
    return new TreePath(myNode.getPath());
  }

  protected JTree getTree() {
    return myNode.getTree();
  }

  @Override
  protected void onShown() {
    myNode.getTree().onEditorShown(myNode);
  }

  @Override
  protected void onHidden() {
    myNode.getTree().onEditorHidden(myNode);
  }
}
