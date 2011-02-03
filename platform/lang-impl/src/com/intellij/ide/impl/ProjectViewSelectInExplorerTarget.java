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
package com.intellij.ide.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Roman.Chernyatchik
 */
public class ProjectViewSelectInExplorerTarget implements SelectInTarget, DumbAware {
  @Override
  public boolean canSelect(final SelectInContext context) {
    final VirtualFile file = context.getVirtualFile();
    return RevealFileAction.isLocalFile(file);
  }

  @Override
  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final VirtualFile file = context.getVirtualFile();
    assert file != null;

    RevealFileAction.revealFile(file);
  }

  @Override
  public String getToolWindowId() {
    return null;
  }

  @Override
  public String getMinorViewId() {
    return null;
  }

  @Override
  public String toString() {
    return RevealFileAction.getActionName();
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.OS_FILE_MANAGER;
  }
}
