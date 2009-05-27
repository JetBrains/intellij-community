package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.ui.playback.PlaybackRunner;

import java.awt.*;
import java.awt.event.KeyEvent;

public class AlphaNumericTypeCommand extends TypeCommand {

  public AlphaNumericTypeCommand(String text, int line) {
    super(text, line);
  }

  public ActionCallback _execute(PlaybackRunner.StatusCallback cb, Robot robot) {
    final String text = getText();
    for (int i = 0; i < text.length(); i++) {
      final char each = text.charAt(i);
      if ('\\' == each && i + 1 < text.length()) {
        final char next = text.charAt(i + 1);
        boolean processed = true;
        switch (next) {
          case 'n':
            type(robot, KeyEvent.VK_ENTER, 0);
            break;
          case 't':
            type(robot, KeyEvent.VK_TAB, 0);
            break;
          case 'r':
            type(robot, KeyEvent.VK_ENTER, 0);
            break;
          default:
            processed = false;
        }

        if (processed) {
          i++;
          continue;
        }
      }
      type(robot, get(each));
    }
    return new ActionCallback.Done();
  }
}