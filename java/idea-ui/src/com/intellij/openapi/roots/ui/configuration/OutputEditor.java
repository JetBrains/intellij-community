// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
    final var panel = new OutputEditorUi().createPanel(myCompilerOutputEditor, myJavadocEditor, myAnnotationsEditor);
    return ScrollPaneFactory.createScrollPane(panel);
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
