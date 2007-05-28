package com.intellij.compiler.ant;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.ant.taskdefs.Exclude;
import com.intellij.compiler.ant.taskdefs.Include;
import com.intellij.compiler.ant.taskdefs.PatternSet;
import com.intellij.openapi.project.Project;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class CompilerResourcePatterns extends Generator{
  private final PatternSet myPatternSet;

  public CompilerResourcePatterns(Project project) {
    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    final String[] patterns = compilerConfiguration.getResourceFilePatterns();
    myPatternSet = new PatternSet(BuildProperties.PROPERTY_COMPILER_RESOURCE_PATTERNS);
    for (String pattern : patterns) {
      if (compilerConfiguration.isPatternNegated(pattern)) {
        myPatternSet.add(new Exclude("**/" + pattern.substring(1)));
      }
      else {
        myPatternSet.add(new Include("**/" + pattern));
      }
    }
  }



  public void generate(DataOutput out) throws IOException {
    myPatternSet.generate(out);
  }


}
