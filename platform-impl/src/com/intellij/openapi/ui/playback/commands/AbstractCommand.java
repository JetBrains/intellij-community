package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackCommand;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.ActionCallback;

import java.awt.*;

public abstract class AbstractCommand implements PlaybackCommand {

  public static String CMD_PREFIX = "%";

  private String myText;
  private int myLine;

  public AbstractCommand(String text, int line) {
    myText = text != null ? text : null;
    myLine = line;
  }

  public String getText() {
    return myText;
  }

  public int getLine() {
    return myLine;
  }

  public boolean canGoFurther() {
    return true;
  }

  public final ActionCallback execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    try {
      dumpCommand(cb);
      return _execute(cb, robot);
    }
    catch (Exception e) {
      cb.error(e.getMessage(), getLine());
      return new ActionCallback.Rejected();
    }
  }

  protected abstract ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot);

  public void dumpCommand(final PlaybackRunner.StatusCallback cb) {
    cb.message(getText(), getLine());
  }

  public void dumpError(final PlaybackRunner.StatusCallback cb, final String text) {
    cb.error(text, getLine());
  }
}