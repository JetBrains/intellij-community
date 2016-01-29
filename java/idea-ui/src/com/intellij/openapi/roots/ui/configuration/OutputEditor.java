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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 12-Aug-2006
 * Time: 20:14:02
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class OutputEditor extends ModuleElementsEditor {
  private final BuildElementsEditor myCompilerOutputEditor;
  private final JavadocEditor myJavadocEditor;
  private final AnnotationsEditor myAnnotationsEditor;

  protected OutputEditor(final ModuleConfigurationState state) {
    super(state);
    myCompilerOutputEditor = new BuildElementsEditor(state);
    myJavadocEditor = new JavadocEditor(state);
    myAnnotationsEditor = new AnnotationsEditor(state);
  }

  @Override
  protected JComponent createComponentImpl() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));
    final GridBagConstraints gc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0);
    panel.add(myCompilerOutputEditor.createComponentImpl(), gc);
    final JPanel javadocPanel = (JPanel)myJavadocEditor.createComponentImpl();
    javadocPanel.setBorder(IdeBorderFactory.createTitledBorder(myJavadocEditor.getDisplayName(), false));
    gc.weighty = 1;
    panel.add(javadocPanel, gc);
    final JPanel annotationsPanel = (JPanel)myAnnotationsEditor.createComponentImpl();
    annotationsPanel.setBorder(IdeBorderFactory.createTitledBorder(myAnnotationsEditor.getDisplayName(), false));
    panel.add(annotationsPanel, gc);
    return panel;
  }

  @Override
  public void saveData() {
    myCompilerOutputEditor.saveData();
    myJavadocEditor.saveData();
    myAnnotationsEditor.saveData();
  }

  @Override
  public String getDisplayName() {
    return ProjectBundle.message("project.roots.path.tab.title");
  }


  @Override
  public void moduleStateChanged() {
    super.moduleStateChanged();
    myCompilerOutputEditor.moduleStateChanged();
    myJavadocEditor.moduleStateChanged();
    myAnnotationsEditor.moduleStateChanged();
  }


  @Override
  public void moduleCompileOutputChanged(final String baseUrl, final String moduleName) {
    super.moduleCompileOutputChanged(baseUrl, moduleName);
    myCompilerOutputEditor.moduleCompileOutputChanged(baseUrl, moduleName);
    myJavadocEditor.moduleCompileOutputChanged(baseUrl, moduleName);
    myAnnotationsEditor.moduleCompileOutputChanged(baseUrl, moduleName);
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "projectStructure.modules.paths";
  }
}
