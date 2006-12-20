package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class PathElement extends Tag{

  public PathElement(@NonNls String dir) {
    //noinspection HardCodedStringLiteral
    super("pathelement", new Pair[]{new Pair<String, String>("location", dir)});
  }

}

