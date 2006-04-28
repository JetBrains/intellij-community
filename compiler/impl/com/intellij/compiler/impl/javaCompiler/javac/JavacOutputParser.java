package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.compiler.OutputParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.rt.compiler.JavacResourcesReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavacOutputParser extends OutputParser {
  private int myTabSize;
  private @NonNls String WARNING_PREFIX = "warning:"; // default value

  public JavacOutputParser(Project project) {
    myTabSize = CodeStyleSettingsManager.getSettings(project).getTabSize(StdFileTypes.JAVA);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // emulate patterns setup if 'embedded' javac is used (javac is started not via JavacRunner)
      addJavacPattern(JavacResourcesReader.MSG_PARSING_STARTED + JavacResourcesReader.CATEGORY_VALUE_DIVIDER + "[parsing started {0}]");
      addJavacPattern(JavacResourcesReader.MSG_PARSING_COMPLETED + JavacResourcesReader.CATEGORY_VALUE_DIVIDER + "[parsing completed {0}ms]");
      addJavacPattern(JavacResourcesReader.MSG_LOADING + JavacResourcesReader.CATEGORY_VALUE_DIVIDER + "[loading {0}]");
      addJavacPattern(JavacResourcesReader.MSG_CHECKING + JavacResourcesReader.CATEGORY_VALUE_DIVIDER + "[checking {0}]");
      addJavacPattern(JavacResourcesReader.MSG_WROTE + JavacResourcesReader.CATEGORY_VALUE_DIVIDER + "[wrote {0}]");
    }
  }

  public boolean processMessageLine(Callback callback) {
    if (super.processMessageLine(callback)) {
      return true;
    }                                              
    final String line = callback.getCurrentLine();
    if (line == null) {
      return false;
    }
    if (JavacResourcesReader.MSG_PATTERNS_START.equals(line)) {
      myParserActions.clear();
      while (true) {
        final String patternLine = callback.getNextLine();
        if (JavacResourcesReader.MSG_PATTERNS_END.equals(patternLine)) {
          break;
        }
        addJavacPattern(patternLine);
      }
      return true;
    }

    int colonIndex1 = line.indexOf(':');
    if (colonIndex1 == 1){ // drive letter
      colonIndex1 = line.indexOf(':', colonIndex1 + 1);
    }

    if (colonIndex1 >= 0){ // looks like found something like file path
      @NonNls String part1 = line.substring(0, colonIndex1).trim();
      if(part1.equalsIgnoreCase("error")) { // jikes
        addMessage(callback, CompilerMessageCategory.ERROR, line.substring(colonIndex1));
        return true;
      }
      if(part1.equalsIgnoreCase("warning")) {
        addMessage(callback, CompilerMessageCategory.WARNING, line.substring(colonIndex1));
        return true;
      }
      if(part1.equals("javac")) {
        addMessage(callback, CompilerMessageCategory.ERROR, line);
        return true;
      }

      final int colonIndex2 = line.indexOf(':', colonIndex1 + 1);
      if (colonIndex2 >= 0){
        final String filePath = part1.replace(File.separatorChar, '/');
        final Boolean fileExists = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>(){
          public Boolean compute(){
            return LocalFileSystem.getInstance().findFileByPath(filePath) != null? Boolean.TRUE : Boolean.FALSE;
          }
        });
        if (!fileExists.booleanValue()) {
          // the part one turned out to be something else than a file path
          return true;
        }
        try {
          final int lineNum = Integer.parseInt(line.substring(colonIndex1 + 1, colonIndex2).trim());
          String message = line.substring(colonIndex2 + 1).trim();
          CompilerMessageCategory category = CompilerMessageCategory.ERROR;
          if (message.startsWith(WARNING_PREFIX)){
            message = message.substring(WARNING_PREFIX.length()).trim();
            category = CompilerMessageCategory.WARNING;
          }

          List<String> messages = new ArrayList<String>();
          messages.add(message);
          int colNum;
          String prevLine = null;
          do{
            final String nextLine = callback.getNextLine();
            if (nextLine == null) {
              return false;
            }
            if (nextLine.trim().equals("^")){
              final int fakeColNum = nextLine.indexOf('^') + 1;
              final CharSequence chars = prevLine == null ? line : prevLine;
              final int offsetColNum = EditorUtil.calcOffset(null, chars, 0, chars.length(), fakeColNum, 8);
              colNum = EditorUtil.calcColumnNumber(null, chars,0, offsetColNum, myTabSize);
              break;
            }
            if (prevLine != null) {
              messages.add(prevLine);
            }
            prevLine = nextLine;
          }
          while(true);

          if (colNum > 0){
            messages = convertMessages(messages);
            StringBuffer buf = new StringBuffer();
            for (final String m : messages) {
              if (buf.length() > 0) {
                buf.append("\n");
              }
              buf.append(m);
            }
            addMessage(callback, category, buf.toString(), VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, filePath), lineNum, colNum);
            return true;
          }
        }
        catch (NumberFormatException e) {
        }
      }
    }

    if(line.endsWith("java.lang.OutOfMemoryError")) {
      addMessage(callback, CompilerMessageCategory.ERROR, CompilerBundle.message("error.javac.out.of.memory"));
      return true;
    }

    addMessage(callback, CompilerMessageCategory.INFORMATION, line);
    return true;
  }


  private static List<String> convertMessages(List<String> messages) {
    if(messages.size() <= 1) {
      return messages;
    }
    final String line0 = messages.get(0);
    final String line1 = messages.get(1);
    final int colonIndex = line1.indexOf(':');
    if (colonIndex > 0){
      @NonNls String part1 = line1.substring(0, colonIndex).trim();
      // jikes
      if ("symbol".equals(part1)){
        String symbol = line1.substring(colonIndex + 1).trim();
        messages.remove(1);
        if(messages.size() >= 2) {
          messages.remove(1);
        }
        messages.set(0, line0 + " " + symbol);
      }
    }
    return messages;
  }

  private void addJavacPattern(@NonNls final String line) {
    final int dividerIndex = line.indexOf(JavacResourcesReader.CATEGORY_VALUE_DIVIDER);
    if (dividerIndex < 0) {
      // by reports it may happen for some IBM JDKs (empty string?)
      return;
    }
    final String category = line.substring(0, dividerIndex);
    final String resourceBundleValue = line.substring(dividerIndex + 1);
    if (JavacResourcesReader.MSG_PARSING_COMPLETED.equals(category) ||
        JavacResourcesReader.MSG_PARSING_STARTED.equals(category) ||
        JavacResourcesReader.MSG_WROTE.equals(category)
      ) {
      myParserActions.add(new FilePathActionJavac(createMatcher(resourceBundleValue)));
    }
    else if (JavacResourcesReader.MSG_CHECKING.equals(category)) {
      myParserActions.add(new JavacParserAction(createMatcher(resourceBundleValue)) {
        protected void doExecute(String parsedData, final Callback callback) {
          callback.setProgressText(CompilerBundle.message("progress.compiling.class", parsedData));
        }
      });
    }
    else if (JavacResourcesReader.MSG_LOADING.equals(category)) {
      myParserActions.add(new JavacParserAction(createMatcher(resourceBundleValue)) {
        protected void doExecute(@Nullable String parsedData, final Callback callback) {
          callback.setProgressText(CompilerBundle.message("progress.loading.classes"));
        }
      });
    }
    else if (JavacResourcesReader.MSG_WARNING.equals(category)) {
      WARNING_PREFIX = resourceBundleValue;
    }
    else if (JavacResourcesReader.MSG_STATISTICS.equals(category)) {
      myParserActions.add(new JavacParserAction(createMatcher(resourceBundleValue)) {
        protected void doExecute(@Nullable String parsedData, final Callback callback) {
          // empty
        }
      });
    }
  }


  /**
   * made public for Tests, do not use this method directly
   */
  public static Matcher createMatcher(@NonNls final String resourceBundleValue) {
    @NonNls String regexp = resourceBundleValue.replaceAll("([\\[\\]\\(\\)\\.\\*])", "\\\\$1");
    regexp = regexp.replaceAll("\\{\\d+\\}", "(.+)");
    return Pattern.compile(regexp, Pattern.CASE_INSENSITIVE).matcher("");
  }

  public boolean isTrimLines() {
    return false;
  }
}