package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Exclude;
import com.intellij.compiler.ant.taskdefs.PatternSet;
import com.intellij.openapi.fileTypes.FileTypeManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class IgnoredFiles extends Generator{
  private final PatternSet myPatternSet;

  public IgnoredFiles() {
    myPatternSet = new PatternSet(BuildProperties.PROPERTY_IGNORED_FILES);
    final StringTokenizer tokenizer = new StringTokenizer(FileTypeManager.getInstance().getIgnoredFilesList(), ";", false);
    while(tokenizer.hasMoreTokens()) {
      final String filemask = tokenizer.nextToken();
      myPatternSet.add(new Exclude("**/" + filemask + "/**"));
    }
  }



  public void generate(PrintWriter out) throws IOException {
    myPatternSet.generate(out);
  }
}
