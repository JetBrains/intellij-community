package com.intellij.compiler;

import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public abstract class OutputParser {
  protected final List<ParserAction> myParserActions = new ArrayList<ParserAction>(10);

  public static interface Callback {
    @NonNls String getNextLine();        
    @NonNls String getCurrentLine();
    void setProgressText(String text);
    void fileProcessed(@NonNls String path);
    void fileGenerated(@NonNls String path);
    void message(CompilerMessageCategory category, String message, @NonNls String url, int lineNum, int columnNum);
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

  protected static void addMessage(Callback callback, CompilerMessageCategory type, String message) {
    if(message == null || message.trim().length() == 0) {
      return;
    }
    addMessage(callback, type, message, null, -1, -1);
  }

  protected static void addMessage(Callback callback, CompilerMessageCategory type, String text, String url, int line, int column){
    callback.message(type, text, url, line, column);
  }

  public boolean isTrimLines() {
    return true;
  }
}