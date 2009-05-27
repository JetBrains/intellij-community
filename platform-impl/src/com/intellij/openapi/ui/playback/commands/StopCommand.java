package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.ui.playback.PlaybackRunner;

import java.awt.*;

public class StopCommand extends AbstractCommand {

  public static String PREFIX = CMD_PREFIX + "stop";

  public StopCommand(String text, int line) {
    super(text, line);
  }

  protected ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    cb.message("Stopped", getLine());
    return new ActionCallback.Done();
  }

  @Override
  public boolean canGoFurther() {
    return false;
  }
}