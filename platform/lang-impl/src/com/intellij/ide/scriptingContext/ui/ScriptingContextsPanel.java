/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.scriptingContext.ui;

import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingContextsPanel {
  private JPanel myTopPanel;
  private JButton myAddContextButton;
  private JButton myRemoveContextButton;
  private JButton myEditContextButton;
  private JPanel myScriptingContextsPanel;
  private JPanel myPatternsPanel;
  private JButton myAddPatternButton;
  private JButton myRemovePatternButton;
  private JButton myEditPatternButton;
  private JBTable myPatternTable;
  private JBList myContextsList;

  public JPanel getPanel() {
    return myTopPanel;
  }

}
