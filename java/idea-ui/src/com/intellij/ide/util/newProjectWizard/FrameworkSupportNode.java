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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.CheckedTreeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
* @author nik
*/
public class FrameworkSupportNode extends CheckedTreeNode {
  private final FrameworkSupportInModuleProvider myProvider;
  private final FrameworkSupportNode myParentNode;
  private FrameworkSupportInModuleConfigurable myConfigurable;
  private final List<FrameworkSupportNode> myChildren = new ArrayList<FrameworkSupportNode>();
  private final FrameworkSupportModelBase myModel;
  private final Disposable myParentDisposable;

  public FrameworkSupportNode(final FrameworkSupportInModuleProvider provider, final FrameworkSupportNode parentNode, final FrameworkSupportModelBase model,
                              Disposable parentDisposable) {
    super(provider);
    myParentDisposable = parentDisposable;
    setChecked(false);
    myProvider = provider;
    myParentNode = parentNode;
    model.registerComponent(provider, this);
    myModel = model;
    if (parentNode != null) {
      parentNode.add(this);
      parentNode.myChildren.add(this);
    }
  }

  public List<FrameworkSupportNode> getChildren() {
    return myChildren;
  }

  public FrameworkSupportInModuleProvider getProvider() {
    return myProvider;
  }

  public FrameworkSupportNode getParentNode() {
    return myParentNode;
  }

  public synchronized FrameworkSupportInModuleConfigurable getConfigurable() {
    if (myConfigurable == null) {
      myConfigurable = myProvider.createConfigurable(myModel);
      Disposer.register(myParentDisposable, myConfigurable);
    }
    return myConfigurable;
  }

  public static void sortByName(List<FrameworkSupportNode> nodes) {
    if (nodes.isEmpty()) return;

    Collections.sort(nodes, new Comparator<FrameworkSupportNode>() {
      public int compare(final FrameworkSupportNode o1, final FrameworkSupportNode o2) {
        return o1.getTitle().compareToIgnoreCase(o2.getTitle());
      }
    });
    for (FrameworkSupportNode node : nodes) {
      node.sortChildren();
    }
  }

  public String getTitle() {
    return myProvider.getFrameworkType().getPresentableName();
  }

  private void sortChildren() {
    sortByName(myChildren);
  }
}
