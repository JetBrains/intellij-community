package com.intellij.openapi.command.impl;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ContentChange;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.ChangeRevertionVisitor;

public class ChangeRangeRevertionVisitor extends ChangeRevertionVisitor {
  private final Change myFromChange;
  private final Change myToChange;

  private boolean isReverting;

  public ChangeRangeRevertionVisitor(IdeaGateway gw, Change from, Change to) {
    super(gw);
    myFromChange = from;
    myToChange = to;
  }

  @Override
  protected boolean shouldRevert(Change c) {
    if (c == myFromChange) {
      isReverting = true;
    }
    return isReverting && !(c instanceof ContentChange);
  }

  @Override
  protected void checkShouldStop(Change c) throws StopVisitingException {
    if (c == myToChange) stop();
  }
}