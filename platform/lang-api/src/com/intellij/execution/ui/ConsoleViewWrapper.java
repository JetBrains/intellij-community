/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.ui;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Gregory.Shrago
 */
public class ConsoleViewWrapper implements ConsoleView, ExecutionConsoleEx {
  private final ConsoleView myDelegate;

  public ConsoleViewWrapper(final ConsoleView delegate) {
    myDelegate = delegate;
  }

  public ConsoleView getDelegate() {
    return myDelegate;
  }

  public void dispose() {
    myDelegate.dispose();
  }

  public JComponent getComponent() {
    return myDelegate.getComponent();
  }

  public JComponent getPreferredFocusableComponent() {
    return myDelegate.getPreferredFocusableComponent();
  }

  public void print(String s, ConsoleViewContentType contentType) {
    myDelegate.print(s, contentType);
  }

  public void clear() {
    myDelegate.clear();
  }

  public void scrollTo(int offset) {
    myDelegate.scrollTo(offset);
  }

  public void attachToProcess(ProcessHandler processHandler) {
    myDelegate.attachToProcess(processHandler);
  }

  public void setOutputPaused(boolean value) {
    myDelegate.setOutputPaused(value);
  }

  public boolean isOutputPaused() {
    return myDelegate.isOutputPaused();
  }

  public boolean hasDeferredOutput() {
    return myDelegate.hasDeferredOutput();
  }

  public void performWhenNoDeferredOutput(Runnable runnable) {
    myDelegate.performWhenNoDeferredOutput(runnable);
  }

  public void setHelpId(String helpId) {
    myDelegate.setHelpId(helpId);
  }

  public void addMessageFilter(Filter filter) {
    myDelegate.addMessageFilter(filter);
  }

  public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {
    myDelegate.printHyperlink(hyperlinkText, info);
  }

  public int getContentSize() {
    return myDelegate.getContentSize();
  }

  public boolean canPause() {
    return myDelegate.canPause();
  }

  public void buildUi(RunnerLayoutUi layoutUi) {
    if (myDelegate instanceof ExecutionConsoleEx) {
      ((ExecutionConsoleEx)myDelegate).buildUi(layoutUi);
    }
  }


  public String getExecutionConsoleId() {
    if (myDelegate instanceof ExecutionConsoleEx) {
      return ((ExecutionConsoleEx)myDelegate).getExecutionConsoleId();
    }
    return null;
  }

  @NotNull
  public AnAction[] createConsoleActions() {
    return myDelegate.createConsoleActions();
  }

}
