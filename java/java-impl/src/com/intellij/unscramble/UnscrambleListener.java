/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.unscramble;

import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.wm.IdeFrame;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class UnscrambleListener extends ApplicationAdapter {
  private static final int MAX_STACKTRACE_SIZE = 15 * 1024; //15Kb
  private String stacktrace = null;

  @Override
  public void applicationActivated(IdeFrame ideFrame) {
    final String clipboard = getClipboardContents();
    if (clipboard != null && clipboard.length() < MAX_STACKTRACE_SIZE && !clipboard.equals(stacktrace)) {
      stacktrace = clipboard;
      if (isStacktrace(stacktrace)) {
        new UnscrambleDialog(ideFrame.getProject()).doOKAction();
      }
    }
  }

  @Override
  public void applicationDeactivated(IdeFrame ideFrame) {
    stacktrace = getClipboardContents();
  }

  public static String getClipboardContents() {
    String result = "";
    try {
      final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      final Transferable contents = clipboard.getContents(null);
      final boolean hasTransferableText = (contents != null) && contents.isDataFlavorSupported(DataFlavor.stringFlavor);
      if (hasTransferableText) {
        result = (String)contents.getTransferData(DataFlavor.stringFlavor);
      }
    }
    catch (Exception e) {//
    }
    return result;
  }

  private static final Pattern STACKTRACE_LINE =
    Pattern.compile("[\t]*at [[a-zA-Z0-9]+\\.]+[a-zA-Z$0-9]+\\.[a-zA-Z0-9_]+\\([A-Za-z0-9_]+\\.java:[\\d]+\\)");

  public static boolean isStacktrace(String stacktrace) {
    int linesCount = 0;
    for (String line : stacktrace.split("\n")) {
      line = line.trim();
      if (line.endsWith("\r")) {
        line = line.substring(0, line.length() - 1);
      }
      if (STACKTRACE_LINE.matcher(line).matches()) {
        linesCount++;
      }
      else {
        linesCount = 0;
      }
      if (linesCount > 2) return true;
    }
    return false;
  }
}
