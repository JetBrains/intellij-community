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
public class JavaMethodBreakpointProperties extends JavaBreakpointProperties<JavaMethodBreakpointProperties> {
  @Attribute("class")
  public String myClassPattern;

  @Attribute("method")
  public String myMethodName;

  public boolean WATCH_ENTRY = true;
  public boolean WATCH_EXIT  = true;

  public JavaMethodBreakpointProperties(String classPattern, String methodName) {
    myClassPattern = classPattern;
    myMethodName = methodName;
  }

  public JavaMethodBreakpointProperties() {
  }

  @Nullable
  @Override
  public JavaMethodBreakpointProperties getState() {
    return this;
  }

  @Override
  public void loadState(JavaMethodBreakpointProperties state) {
    super.loadState(state);

    myClassPattern = state.myClassPattern;
    myMethodName = state.myMethodName;

    WATCH_ENTRY = state.WATCH_ENTRY;
    WATCH_EXIT = state.WATCH_EXIT;
  }
}
