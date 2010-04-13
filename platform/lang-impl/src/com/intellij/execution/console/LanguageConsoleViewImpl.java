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
package com.intellij.execution.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;

import javax.swing.*;

/**
 * @author Gregory.Shrago
 */
public class LanguageConsoleViewImpl extends ConsoleViewImpl {
  protected LanguageConsoleImpl myConsole;

  public LanguageConsoleViewImpl(final Project project, String title, final Language language) {
    this(project, new LanguageConsoleImpl(project, title, language));
  }

  protected LanguageConsoleViewImpl(final Project project, final LanguageConsoleImpl console) {
    super(project, true);
    myConsole = console;
    Disposer.register(this, myConsole);
    Disposer.register(project, this);
  }

  public LanguageConsoleImpl getConsole() {
    return myConsole;
  }

  @Override
  protected EditorEx createRealEditor() {
    return myConsole.getHistoryViewer();
  }

  @Override
  protected void disposeEditor() {
  }

  @Override
  protected JComponent createCenterComponent() {
    return myConsole.getComponent();
  }

  public JComponent getComponent() {
    return super.getComponent();
  }

  public JComponent getPreferredFocusableComponent() {
    return myConsole.getConsoleEditor().getContentComponent();
  }
}
