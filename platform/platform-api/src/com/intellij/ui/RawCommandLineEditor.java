// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.execution.ui.FragmentWrapper;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.builder.VerticalComponentGap;
import com.intellij.util.Function;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.util.List;

import static com.intellij.ui.dsl.gridLayout.UnscaledGapsKt.toUnscaledGaps;

public class RawCommandLineEditor extends JPanel implements TextAccessor, FragmentWrapper {
  private final ExpandableTextField myEditor;
  private String myDialogCaption = "";

  public RawCommandLineEditor() {
    this(ParametersListUtil.DEFAULT_LINE_PARSER, ParametersListUtil.DEFAULT_LINE_JOINER);
  }

  public RawCommandLineEditor(final Function<? super String, ? extends List<String>> lineParser, final Function<? super List<String>, String> lineJoiner) {
    super(new BorderLayout());
    myEditor = new ExpandableTextField(lineParser, lineJoiner);
    // required! otherwise JPanel will occasionally gain focus instead of the component
    setFocusable(false);
    add(myEditor, BorderLayout.CENTER);
    setDescriptor(null);
    putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH);
    putClientProperty(DslComponentProperty.INTERACTIVE_COMPONENT, myEditor);
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, toUnscaledGaps(myEditor.getInsets()));
  }

  public void setDescriptor(FileChooserDescriptor descriptor) {
    setDescriptor(descriptor, true);
  }

  public void setDescriptor(FileChooserDescriptor descriptor, boolean insertSystemDependentPaths) {
    InsertPathAction.addTo(myEditor, descriptor, insertSystemDependentPaths);
  }

  /**
   * @deprecated Won't be used anymore as dialog is replaced with lightweight popup
   */
  @Deprecated(forRemoval = true)
  public String getDialogCaption() {
    return myDialogCaption;
  }

  /**
   * @deprecated Won't be used anymore as dialog is replaced with lightweight popup
   */
  @Deprecated
  public void setDialogCaption(String dialogCaption) {
    myDialogCaption = dialogCaption != null ? dialogCaption : "";
  }

  @Override
  public void setText(@Nullable String text) {
    myEditor.setText(text);
  }

  @Override
  public String getText() {
    return StringUtil.notNullize(myEditor.getText());
  }

  public JTextField getTextField() {
    return myEditor;
  }

  public ExpandableTextField getEditorField() {
    return myEditor;
  }

  public Document getDocument() {
    return myEditor.getDocument();
  }

  public void attachLabel(JLabel label) {
    label.setLabelFor(myEditor);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myEditor.setEnabled(enabled);
  }

  public @NotNull RawCommandLineEditor withMonospaced(boolean monospaced) {
    myEditor.setMonospaced(monospaced);
    return this;
  }

  @Override
  public JComponent getComponentToRegister() {
    return getEditorField();
  }
}
