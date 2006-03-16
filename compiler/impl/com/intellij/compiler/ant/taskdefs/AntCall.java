package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class AntCall extends Tag{

  public AntCall(final String target) {
    super("antcall", new Pair[] {new Pair<String, String>("target", target)});
  }
}
