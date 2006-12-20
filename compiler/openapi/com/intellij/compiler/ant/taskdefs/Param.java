package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Param extends Tag {
  public Param(@NonNls final String name, final String value) {
    //noinspection HardCodedStringLiteral
    super("param", new Pair[] {
      new Pair<String, String>("name", name),
      new Pair<String, String>("value", value)
    });
  }

}
