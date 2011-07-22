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
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.impl.FrameworkDetectionContextImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class SetupDetectedFrameworksDialog extends DialogWrapper {
  private final JPanel myMainPanel;
  private final DetectedFrameworksTree myTree;

  public SetupDetectedFrameworksDialog(Project project, List<DetectedFrameworkDescription> descriptions) {
    super(project, true);
    setTitle("Setup Frameworks");
    myMainPanel = new JPanel(new BorderLayout());
    final FrameworkDetectionContextImpl context = new FrameworkDetectionContextImpl(project);
    myTree = new DetectedFrameworksTree(descriptions, context);
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myTree));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public List<DetectedFrameworkDescription> getSelectedFrameworks() {
    return Arrays.asList(myTree.getCheckedNodes(DetectedFrameworkDescription.class, null));
  }
}
