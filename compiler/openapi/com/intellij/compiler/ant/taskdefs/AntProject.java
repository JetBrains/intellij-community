package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 25, 2004
 */
public class AntProject extends Tag {
  public AntProject(@NonNls String name, @NonNls String defaultTarget) {
    //noinspection HardCodedStringLiteral
    super("project", new Pair[]{new Pair<String, String>("name", name), new Pair<String, String>("default", defaultTarget)});
  }
}
