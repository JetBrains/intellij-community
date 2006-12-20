package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class DirSet extends Tag{

  public DirSet(@NonNls final String dir) {
    //noinspection HardCodedStringLiteral
    super("dirset", new Pair[] {new Pair<String, String>("dir", dir)});
  }
}
