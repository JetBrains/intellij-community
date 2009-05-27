package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.ui.playback.PlaybackRunner;

import java.awt.*;

public class EmptyCommand extends AbstractCommand {
  public EmptyCommand(int line) {
    super("", line);
  }

  public ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    return new ActionCallback.Done();
  }
}