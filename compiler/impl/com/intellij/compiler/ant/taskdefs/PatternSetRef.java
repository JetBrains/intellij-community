package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class PatternSetRef extends Tag{
  public PatternSetRef(@NonNls final String refid) {
    //noinspection HardCodedStringLiteral
    super("patternset", new Pair[] {new Pair<String, String>("refid", refid)});
  }
}
