/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Target extends Tag{
  public Target(@NonNls String name, @Nullable String depends, @Nullable String description, @Nullable String unlessCondition) {
    super("target", getOptions(name, depends, description, unlessCondition));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static Pair[] getOptions(@NonNls String name, @Nullable @NonNls String depends, @Nullable String description, @Nullable @NonNls String unlessCondition) {
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
