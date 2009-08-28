package com.intellij.openapi.actionSystem;

public class AnActionEventVisitor {

  public void visitEvent(AnActionEvent e) {

  }

  public void visitGestureInitEvent(AnActionEvent e) {
    visitEvent(e);
  }

  public void visitGesturePerformedEvent(AnActionEvent e) {
    visitEvent(e);
  }

  public void visitGestureFinishEvent(AnActionEvent e) {
    visitEvent(e);
  }

}