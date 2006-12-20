package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 17, 2004
 */
public class Mkdir extends Tag {
  public Mkdir(@NonNls String directory) {
    //noinspection HardCodedStringLiteral
    super("mkdir", new Pair[] {new Pair<String, String>("dir", directory)});
  }
}
