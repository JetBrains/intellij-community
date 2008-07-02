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
public class ConsoleViewWrapper implements ConsoleView {
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

  @NotNull
  public AnAction[] createUpDownStacktraceActions() {
    return myDelegate.createUpDownStacktraceActions();
  }

}
