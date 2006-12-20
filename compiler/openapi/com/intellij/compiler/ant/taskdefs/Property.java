package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Property extends Tag {

  public Property(@NonNls final String name, final String value) {
    //noinspection HardCodedStringLiteral
    super("property", new Pair[] {
      new Pair<String, String>("name", name),
      new Pair<String, String>("value", value)
    });
  }

  public Property(@NonNls final String filePath) {
    //noinspection HardCodedStringLiteral
    super("property", new Pair[] {
      new Pair<String, String>("file", filePath),
    });
  }

}
