/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui.tree;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;

/**
 * @author Dmitry Batkovich
 */
public class InspectionSeverityGroupNode extends InspectionTreeNode<HighlightDisplayLevel> {
  public InspectionSeverityGroupNode(final Project project, final HighlightDisplayLevel level) {
    super(project, level);
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return getValue().getIcon();
  }

  @Override
  public boolean appearsBold() {
    return true;
  }

  public String toString() {
    return StringUtil.capitalize(getSeverityLevel().toString().toLowerCase());
  }

  public HighlightDisplayLevel getSeverityLevel() {
    HighlightDisplayLevel value = getValue();
    LOG.assertTrue(value != null);
    return value;
  }
}
