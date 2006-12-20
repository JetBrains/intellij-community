package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class PatternSet extends Tag{
  public PatternSet(@NonNls final String id) {
    //noinspection HardCodedStringLiteral
    super("patternset", new Pair[] {new Pair<String, String>("id", id)});
  }
}
