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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Gregory.Shrago
 */
public class LanguageConsoleViewImpl extends ConsoleViewImpl implements LanguageConsoleView {
  @NotNull
  protected LanguageConsoleImpl myConsole;

  public LanguageConsoleViewImpl(Project project, String title, Language language) {
    this(new LanguageConsoleImpl(project, title, language));
  }

  public LanguageConsoleViewImpl(@NotNull LanguageConsoleImpl console) {
    this(console, true);
  }

  public LanguageConsoleViewImpl(@NotNull LanguageConsoleImpl console, boolean usePredefinedMessageFilter) {
    super(console.getProject(), GlobalSearchScope.allScope(console.getProject()), true, usePredefinedMessageFilter);

    myConsole = console;
    Disposer.register(this, myConsole);
  }

  @Override
  @NotNull
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

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myConsole.getConsoleEditor().getContentComponent();
  }
}
