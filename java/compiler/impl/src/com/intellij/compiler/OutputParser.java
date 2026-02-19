// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;

import java.util.ArrayList;
import java.util.List;

public abstract class OutputParser {
  protected final List<ParserAction> myParserActions = new ArrayList<>(10);

  public interface Callback {
    @NlsSafe
    String getNextLine();
    @NlsSafe
    String getCurrentLine();
    void pushBack(@Nls String line);
    void setProgressText(@NlsContexts.ProgressText String text);
    void fileProcessed(@NlsSafe String path);
    void fileGenerated(@NlsSafe String path);
    void message(CompilerMessageCategory category, @Nls String message, @NlsSafe String url, int lineNum, int columnNum);
  }

  public boolean processMessageLine(Callback callback) {
    final String line = callback.getNextLine();
    if(line == null) {
      return false;
    }
    // common statistics messages (javac & jikes)
    for (ParserAction action : myParserActions) {
      if (action.execute(line, callback)) {
        return true;
      }
    }
    if (StringUtil.startsWithChar(line, '[') && StringUtil.endsWithChar(line, ']')) {
      // at this point any meaningful output surrounded with '[' and ']' characters is processed, so
      // suppress messages like "[total 4657ms]" or "[search path for source files: []]"
      return true;
    }
    return false;
  }

  protected static void addMessage(Callback callback, CompilerMessageCategory type, @Nls String message) {
    if(message == null || message.trim().isEmpty()) {
      return;
    }
    addMessage(callback, type, message, null, -1, -1);
  }

  protected static void addMessage(Callback callback, CompilerMessageCategory type, @Nls String text, @NlsSafe String url, int line, int column){
    callback.message(type, text, url, line, column);
  }
}