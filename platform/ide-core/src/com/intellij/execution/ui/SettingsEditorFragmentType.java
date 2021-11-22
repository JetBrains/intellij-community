// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

public enum SettingsEditorFragmentType {
  /**
   * Settings fragment with this type is placed before of all RC fragments.
   * Expected that fragment component will be a {@link com.intellij.execution.ui.BeforeRunComponent}.
   *
   * <p>Note: Fragment with this type can be only one.
   *
   * @see com.intellij.execution.ui.BeforeRunComponent
   * @see com.intellij.execution.ui.BeforeRunFragment#createBeforeRun
   */
  BEFORE_RUN,
  /**
   * Settings fragment with this type is placed in the head of RC
   * (between {@link SettingsEditorFragmentType#BEFORE_RUN} and {@link SettingsEditorFragmentType#COMMAND_LINE}).
   * Expected that it will be a label with bold text and it means RC header text (eg. Run, Build and Run).
   *
   * <p>Note: Fragment with this type can be only one.
   *
   * @see com.intellij.execution.ui.CommonParameterFragments#createHeader
   */
  HEADER,
  /**
   * Settings fragments with this type are placed after RC header.
   * Components of these fragments form command line. IDEA tries to tile command line
   * (it resizes and moves between lines), so that they take up less space.
   * But they will be sorted by fragments {@link SettingsEditorFragment#getPriority() priority}.
   */
  COMMAND_LINE,
  /**
   * Regular RC settings fragments. Usually these are text fields, combo-boxes
   * and simple lists or tables with append/remove buttons and other components with or without label/title.
   * These components are grouped by {@link NestedGroupFragment} in main RC view and
   * by {@link SettingsEditorFragment#getGroup()} in modify options popup
   * and they are sorted inside group by {@link SettingsEditorFragment#getPriority() priority}.
   *
   * <p>Note: Please, use fragments with type {@link SettingsEditorFragmentType#TAG} instead check-boxes.
   *
   * @see com.intellij.openapi.ui.LabeledComponent
   */
  EDITOR,
  /**
   * Short version of {@link SettingsEditorFragmentType#EDITOR} check-box settings fragments.
   * These components are placed after {@link SettingsEditorFragmentType#EDITOR} components
   * in corresponding {@link SettingsEditorFragmentType#EDITOR} group and also ordered by fragment
   * {@link SettingsEditorFragment#getPriority() priority}.
   *
   * @see com.intellij.execution.ui.RunConfigurationEditorFragment#createSettingsTag
   */
  TAG
}
