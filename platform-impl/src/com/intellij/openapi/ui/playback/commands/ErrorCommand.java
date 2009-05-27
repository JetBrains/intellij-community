package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.ui.playback.PlaybackRunner;

import java.awt.*;

public class ErrorCommand extends AbstractCommand {

  public ErrorCommand(String text, int line) {
    super(text, line);
  }

  public ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    dumpError(cb, getText());
    return new ActionCallback.Rejected();
  }
}