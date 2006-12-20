package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Target extends Tag{
  public Target(@NonNls String name, String depends, String description, String unlessCondition) {
    super("target", getOptions(name, depends, description, unlessCondition));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Pair[] getOptions(@NonNls String name, @NonNls String depends, String description, @NonNls String unlessCondition) {
    final List<Pair> options = new ArrayList<Pair>();
    options.add(new Pair<String, String>("name", name));
    if (depends != null && depends.length() > 0) {
      options.add(new Pair<String, String>("depends", depends));
    }
    if (description != null && description.length() > 0) {
      options.add(new Pair<String, String>("description", description));
    }
    if (unlessCondition != null && unlessCondition.length() > 0) {
      options.add(new Pair<String, String>("unless", unlessCondition));
    }
    return options.toArray(new Pair[options.size()]);
  }

}
