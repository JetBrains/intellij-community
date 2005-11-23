package com.intellij.compiler;

import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EclipseCompilerOutputParser extends OutputParser {
  private final String myOutputDir;

  public EclipseCompilerOutputParser(Project project, final String outputDir) {
    myOutputDir = outputDir;
  }

  @NonNls private static final String pathRegex = "\\s*(.*) - #.*";
  @NonNls private static final Pattern pattern = Pattern.compile(pathRegex);
  public boolean processMessageLine(Callback callback) {
    @NonNls String line = callback.getNextLine();
    if (line == null) {
      return false;
    }
    if (line.trim().length() == 0) {
      return true;
    }
    if (line.startsWith("[parsing ")) {
      Matcher matcher = pattern.matcher(line.substring("[parsing ".length()));
      matcher.matches();
      String path = matcher.group(1);
      callback.setProgressText("Parsing "+path);
      callback.fileProcessed(path);
      return true;
    }
    if (line.startsWith("[reading ")) {
      //StringTokenizer tokenizer = new StringTokenizer(line.substring("[reading ".length()), " ]");
      //String fqn = tokenizer.nextToken();
      callback.setProgressText("Reading useless stuff");
      return true;
    }
    if (line.startsWith("[analyzing ")) {
      Matcher matcher = pattern.matcher(line.substring("[analyzing ".length()));
      matcher.matches();
      String path = matcher.group(1);
      callback.setProgressText("Analyzing "+path);
      return true;
    }
    if (line.startsWith("[completed ")) {
      Matcher matcher = pattern.matcher(line.substring("[completed ".length()));
      matcher.matches();
      String path = matcher.group(1);
      callback.setProgressText("Completed "+path);
      return true;
    }
    if (line.startsWith("[writing ")) {
      Matcher matcher = pattern.matcher(line.substring("[writing ".length()));
      matcher.matches();
      String path = matcher.group(1);
      String absPath = myOutputDir + "/" + path;
      callback.setProgressText("Writing "+absPath);
      callback.fileGenerated(absPath);
      return true;
    }

    callback.message(CompilerMessageCategory.INFORMATION, line, null, -1, -1);
    return true;
  }

}
