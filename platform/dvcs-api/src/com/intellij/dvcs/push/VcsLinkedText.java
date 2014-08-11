/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

public class VcsLinkedText {
  @NotNull String myText;
  @NotNull String myHandledLink;

  private final List<TreeNodeLinkListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public VcsLinkedText(@NotNull String text, @NotNull String link) {
    myText = text;
    myHandledLink = link;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getLinkText() {
    return myHandledLink;
  }

  public void addClickListener(@NotNull TreeNodeLinkListener listener) {
    myListeners.add(listener);
  }

  public void fireOnClick(@NotNull DefaultMutableTreeNode relatedNode) {
    for (TreeNodeLinkListener listener : myListeners) {
      listener.onClick(relatedNode);
    }
  }
}
