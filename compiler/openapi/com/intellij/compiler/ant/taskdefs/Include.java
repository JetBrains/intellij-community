package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Include extends Tag {

  public Include(@NonNls final String name) {
    //noinspection HardCodedStringLiteral
    super("include", new Pair[] {new Pair<String, String>("name", name)});
  }

}
