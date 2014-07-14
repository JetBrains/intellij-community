/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class JavaFieldBreakpointProperties extends JavaBreakpointProperties<JavaFieldBreakpointProperties> {
  public boolean WATCH_MODIFICATION = true;
  public boolean WATCH_ACCESS       = false;

  @Attribute("field")
  public String myFieldName;

  @Attribute("class")
  public String myClassName;

  public JavaFieldBreakpointProperties(String fieldName, String className) {
    myFieldName = fieldName;
    myClassName = className;
  }

  public JavaFieldBreakpointProperties() {
  }

  @Nullable
  @Override
  public JavaFieldBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(JavaFieldBreakpointProperties state) {
    super.loadState(state);

    WATCH_MODIFICATION = state.WATCH_MODIFICATION;
    WATCH_ACCESS = state.WATCH_ACCESS;
    myFieldName = state.myFieldName;
    myClassName = state.myClassName;
  }
}
