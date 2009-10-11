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
package com.intellij.ui.treeStructure;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author peter
 * IBM Rational Software Functional Tester used to test Fabrique requires that every node has
 * public String getText()
 * method to provide a string representation for a node. We delegate it to toString()
 */
public class PatchedDefaultMutableTreeNode extends DefaultMutableTreeNode {
  public PatchedDefaultMutableTreeNode() {
  }

  public PatchedDefaultMutableTreeNode(Object userObject) {
    super(userObject);
  }

  public String getText() {
    return String.valueOf(getUserObject());
  }

}
