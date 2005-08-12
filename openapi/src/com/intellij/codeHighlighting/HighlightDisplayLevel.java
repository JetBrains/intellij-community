/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeHighlighting;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.util.Map;

import org.jetbrains.annotations.NonNls;

public class HighlightDisplayLevel {
  private static Map<String, HighlightDisplayLevel> ourMap = new HashMap<String, HighlightDisplayLevel>();

  public static final HighlightDisplayLevel ERROR = new HighlightDisplayLevel("ERROR", IconLoader.getIcon("/general/errorsFound.png"));
  public static final HighlightDisplayLevel WARNING = new HighlightDisplayLevel("WARNING", IconLoader.getIcon("/general/warningsFound.png"));
  public static final HighlightDisplayLevel DO_NOT_SHOW = new HighlightDisplayLevel("DO_NOT_SHOW", IconLoader.getIcon("/general/errorsOK.png"));

  private final String myName;
  private final Icon myIcon;

  public static HighlightDisplayLevel find(String name) {
    return ourMap.get(name);
  }

  private HighlightDisplayLevel(@NonNls String name, Icon icon) {
    myName = name;
    myIcon = icon;
    ourMap.put(myName, this);
  }

  public String toString() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }

}
