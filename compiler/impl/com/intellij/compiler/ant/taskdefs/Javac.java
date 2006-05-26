package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.GenerationOptions;
import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 16, 2004
 */
public class Javac extends Tag {

  public Javac(GenerationOptions genOptions, String moduleName, final String outputDir) {
    super(genOptions.enableFormCompiler ? "javac2" : "javac",
          getAttributes(genOptions, outputDir, moduleName));
  }

  private static Pair[] getAttributes(GenerationOptions genOptions, String outputDir, String moduleName) {
    final List<Pair> pairs = new ArrayList<Pair>();
    pairs.add(pair("destdir", outputDir));
    pairs
      .add(pair("debug", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_GENERATE_DEBUG_INFO)));
    pairs.add(
      pair("nowarn", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_GENERATE_NO_WARNINGS)));
    pairs.add(
      pair("memorymaximumsize", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_MAX_MEMORY)));
    pairs.add(pair("fork", "true"));
    if (genOptions.forceTargetJdk) {
      pairs.add(pair("executable", getExecutable(moduleName)));
    }
    return pairs.toArray(new Pair[pairs.size()]);
  }

  private static String getExecutable(String moduleName) {
    if (moduleName == null) {
      return null;
    }
    //noinspection HardCodedStringLiteral
    return BuildProperties.propertyRef(BuildProperties.getModuleChunkJdkBinProperty(moduleName)) + "/javac";
  }
}
