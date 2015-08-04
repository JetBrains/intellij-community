/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.util.xmlb.annotations.OptionTag;

/**
 * @author egor
 */
public class JavaLineBreakpointProperties extends JavaBreakpointProperties<JavaLineBreakpointProperties> {
  // null - stop at all positions on the line
  // -1 - stop only at the base position (first on the line)
  // 0 or more - index of the lambda on the line to stop at
  private Integer myLambdaOrdinal = null;

  @OptionTag("lambda-ordinal")
  public Integer getLambdaOrdinal() {
    return myLambdaOrdinal;
  }

  public void setLambdaOrdinal(Integer lambdaOrdinal) {
    myLambdaOrdinal = lambdaOrdinal;
  }

  @Override
  public void loadState(JavaLineBreakpointProperties state) {
    super.loadState(state);

    myLambdaOrdinal = state.myLambdaOrdinal;
  }
}
