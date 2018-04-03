// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;

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
  public void loadState(@NotNull JavaLineBreakpointProperties state) {
    super.loadState(state);

    myLambdaOrdinal = state.myLambdaOrdinal;
  }
}
