package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Exclude extends Tag {

  public Exclude(@NonNls final String name) {
    //noinspection HardCodedStringLiteral
    super("exclude", new Pair[] {new Pair<String, String>("name", name)});
  }

}
