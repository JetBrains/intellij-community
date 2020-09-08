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
package com.intellij.packaging.impl.ui;

import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packaging.artifacts.Artifact;

import javax.swing.*;
import java.util.List;

public class ChooseArtifactsDialog extends ChooseElementsDialog<Artifact> {

  public ChooseArtifactsDialog(Project project, List<? extends Artifact> items, @NlsContexts.DialogTitle String title, @NlsContexts.Label String description) {
    super(project, items, title, description, true);
  }

  @Override
  protected String getItemText(Artifact item) {
    return item.getName();
  }

  @Override
  protected Icon getItemIcon(Artifact item) {
    return item.getArtifactType().getIcon();
  }
}