package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFocusManager;

import java.awt.*;

public class AssertFocused extends AbstractCommand {

  public static String PREFIX = CMD_PREFIX + "assert focused";

  public AssertFocused(String text, int line) {
    super(text, line);
  }

  protected ActionCallback _execute(final PlaybackRunner.StatusCallback cb, Robot robot) {
    final String text = getText();
    final String componentName = text.substring(PREFIX.length()).trim();

    if (componentName.length() == 0) {
      dumpError(cb, "Component name expected after " + PREFIX);
      return new ActionCallback.Rejected();
    }

    final ActionCallback result = new ActionCallback();

    IdeFocusManager.findInstance().doWhenFocusSettlesDown(new Runnable() {
      public void run() {
        doAssert(componentName, cb, result);
      }
    });

    return result;
  }

  private void doAssert(String name, PlaybackRunner.StatusCallback status, ActionCallback actionCallback) {
    try {
      final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      assertTrue("No component focused", focusOwner != null);

      Component eachParent = focusOwner;
      String lastFocusedName= null;
      while(eachParent != null) {
        final String eachName = eachParent.getName();

        if (eachName != null) {
          lastFocusedName = eachName;
        }

        if (name.equals(eachName)) {
          actionCallback.setDone();
        }
        eachParent = eachParent.getParent();
      }

      dumpError(status, "Assertion failed, expected focused=" + name + " but was=" + lastFocusedName);
      actionCallback.setRejected();
    }
    catch (AssertionError e) {
      dumpError(status, e.getMessage());
      actionCallback.setRejected();
    }
  }

  private void assertTrue(String text, boolean condition) {
    if (!condition) {
      throw new AssertionError(text);
    }
  }
}