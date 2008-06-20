package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionEventVisitor;

public class GestureActionEvent extends AnActionEvent {
  public GestureActionEvent(KeyboardGestureProcessor processor) {
    super(processor.myContext.actionKey,
          processor.myContext.dataContext,
          processor.myContext.actionPlace,
          processor.myContext.actionPresentation,
          processor.myActionManager,
          0);
  }

  public static class Init extends GestureActionEvent {
    public Init(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    @Override
    public void accept(final AnActionEventVisitor visitor) {
      visitor.visitGestureInitEvent(this);
    }
  }

  public static class PerformAction extends GestureActionEvent {
    public PerformAction(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    @Override
    public void accept(final AnActionEventVisitor visitor) {
      visitor.visitGesturePerformedEvent(this);
    }
  }

  public static class Finish extends GestureActionEvent {
    public Finish(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    @Override
    public void accept(final AnActionEventVisitor visitor) {
      visitor.visitGestureFinishEvent(this);
    }
  }
}