// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class OutputEditor extends ModuleElementsEditor {
  private final BuildElementsEditor myCompilerOutputEditor;
  private final JavadocEditor myJavadocEditor;
  private final AnnotationsEditor myAnnotationsEditor;
  private final List<ModuleElementsEditor> myEditors;

  public OutputEditor(final ModuleConfigurationState state) {
    super(state);
    myCompilerOutputEditor = new BuildElementsEditor(state);
    myJavadocEditor = new JavadocEditor(state);
    myAnnotationsEditor = new AnnotationsEditor(state);
    myEditors = Arrays.asList(myCompilerOutputEditor, myJavadocEditor, myAnnotationsEditor);
    myEditors.forEach(editor -> editor.addListener(this::fireConfigurationChanged));
  }

  @Override
  protected JComponent createComponentImpl() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));
    final GridBagConstraints gc =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                             JBInsets.emptyInsets(), 0, 0);
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
    super.saveData();
    myEditors.forEach(ModuleElementsEditor::saveData);
  }

  @Override
  @NlsContexts.ConfigurableName
  public String getDisplayName() {
    return getName();
  }

  @Override
  public void moduleStateChanged() {
    super.moduleStateChanged();
    myEditors.forEach(ModuleElementsEditor::moduleStateChanged);
  }


  @Override
  public void moduleCompileOutputChanged(final String baseUrl, final String moduleName) {
    super.moduleCompileOutputChanged(baseUrl, moduleName);
    myEditors.forEach(editor -> editor.moduleCompileOutputChanged(baseUrl, moduleName));
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "projectStructure.modules.paths";
  }

  public static @NlsContexts.ConfigurableName String getName() {
    return JavaUiBundle.message("project.roots.path.tab.title");
  }
}
