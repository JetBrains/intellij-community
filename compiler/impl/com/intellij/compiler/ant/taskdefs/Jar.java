package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Jar extends Tag {
  public Jar(@NonNls final String destFile, @NonNls String duplicate) {
    //noinspection HardCodedStringLiteral
    super("jar", new Pair[] {Pair.create("destfile", destFile), Pair.create("duplicate", duplicate)});
  }
}
