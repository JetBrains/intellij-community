// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.ui.panel;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public interface PanelBuilder {
  /**
   * Creates panel from the panel builder settings. <code>GridBagLayout</code> is
   * used internally as the layout manager.
   *
   * @return resulting <code>JPanel</code>
   */
  @NotNull
  JPanel createPanel();

  /**
   * @return <code>true</code> if builder constrains are valid from the design
   * point of view for creating a panel, <code>false</code> otherwise
   */
  boolean constrainsValid();

  /**
   * Adds contents to a concrete panel with the given <code>GridBagConstraints</code>
   * Users should not call this method directly. It is used by the <code>PanelGrid</code>
   * implementation.
   */
  void addToPanel(JPanel panel, GridBagConstraints gc);

  /**
   * @return the the maximum with in columns in terms of <code>GridBagConstraints</code>
   * of the form being created.
   * <p>
   * Users should not call this method directly. It is used by the <code>PanelGrid</code>
   * implementation.
   */
  int gridWidth();
}
