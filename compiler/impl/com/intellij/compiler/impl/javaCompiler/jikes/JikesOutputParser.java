package com.intellij.compiler.impl.javaCompiler.jikes;

import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.compiler.OutputParser;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class JikesOutputParser extends OutputParser {
  private final JikesSettings myJikesSettings;
  @NonNls private static final String JAVA_FILE_MSG_TAIL = ".java:";
  @NonNls private static final String CAUTION = "Caution";
  @NonNls private static final String WARNING = "Warning";
  @NonNls private static final String ERROR = "Error";
  @NonNls private static final String SEMANTIC_WARNING = "Semantic Warning";
  @NonNls private static final String SEMANTIC_ERROR = "Semantic Error";
  @NonNls private static final String ENTER_TO_CONTINUE_REGEXP = ".*Enter\\s+to\\s+continue.*";

  public JikesOutputParser(Project project) {
    myJikesSettings = JikesSettings.getInstance(project);
    myParserActions.add(new ParserActionJikes());
  }

  public boolean processMessageLine(Callback callback) {
    if (super.processMessageLine(callback)) {
      return true;
    }
    String line = callback.getCurrentLine();
    if (line == null) {
      return false;
    }
    if (line.length() == 0) {
      return false;
    }
//sae
    if (myJikesSettings.IS_EMACS_ERRORS_MODE) {

      String filePath = "";
      final int tailIndex = line.indexOf(JAVA_FILE_MSG_TAIL);
      if (tailIndex > 5) filePath = line.substring(0, tailIndex + 5);
      filePath = filePath.replace(File.separatorChar, '/');
      final String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, filePath);
      if (tailIndex > 6) {
        line = line.substring(tailIndex + 6);
        int lineNum;

//second token = start line
        StringTokenizer tokenizer = new StringTokenizer(line, ":");
//first token = filename
        String token = tokenizer.nextToken();

        try {
          lineNum = Integer.parseInt(token);
        }
        catch (Exception e) {
          addMessage(callback, CompilerMessageCategory.INFORMATION, line);
          return true;
        }
//thrd token = start column
        token = tokenizer.nextToken();
        int colNum;
        try {
          colNum = Integer.parseInt(token);
        }
        catch (Exception e) {
          addMessage(callback, CompilerMessageCategory.INFORMATION, line);
          return true;
        }
//4,5 token = end line/column   tmp not used
        tokenizer.nextToken();
        tokenizer.nextToken();
// 6 error type
        CompilerMessageCategory category = CompilerMessageCategory.INFORMATION;
        token = tokenizer.nextToken().trim();
        if (CAUTION.equalsIgnoreCase(token)) {
          category = CompilerMessageCategory.WARNING;
        }
        else if (WARNING.equalsIgnoreCase(token) || SEMANTIC_WARNING.equalsIgnoreCase(token)) { // Semantic errors/warnings were introduced in jikes 1.18
          category = CompilerMessageCategory.WARNING;
        }
        else if (ERROR.equalsIgnoreCase(token) || SEMANTIC_ERROR.equalsIgnoreCase(token)) {
          category = CompilerMessageCategory.ERROR;
        }

        String message = token;
        message = message.concat("  ");
        message = message.concat(tokenizer.nextToken(""));
        ArrayList<String> messages = new ArrayList<String>();
        messages.add(message);

        if (colNum > 0 && messages.size() > 0) {
          StringBuffer buf = new StringBuffer();
          for (String m : messages) {
            if (buf.length() > 0) {
              buf.append("\n");
            }
            buf.append(m);
          }
          addMessage(callback, category, buf.toString(), url, lineNum, colNum);
          return true;
        }
      }
    }
//--sae

//Enter to continue
    if (!line.matches(ENTER_TO_CONTINUE_REGEXP)) {
      addMessage(callback, CompilerMessageCategory.INFORMATION, line);
    }
    return true;
  }
}
