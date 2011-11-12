/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.FileStructureFilter;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class JavaAnonymousMembersFilter implements FileStructureFilter {

  private static final String ID = "SHOW_ANONYMOUS";

  @Override
  public String getCheckBoxText() {
    return "Show Anonymous Classes";
  }

  @Override
  public Shortcut[] getShortcut() {
    return new Shortcut[0];
  }

  @Override
  public boolean isVisible(TreeElement treeNode) {
    return !(treeNode instanceof JavaAnonymousClassTreeElement);
  }

  @Override
  public boolean isReverted() {
    return true;
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(getCheckBoxText(), null, PlatformIcons.ANONYMOUS_CLASS_ICON);
  }

  @NotNull
  @Override
  public String getName() {
    return ID;
  }
}
