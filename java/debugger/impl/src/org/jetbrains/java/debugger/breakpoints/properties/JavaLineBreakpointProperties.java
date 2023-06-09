// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

public class JavaLineBreakpointProperties extends JavaBreakpointProperties<JavaLineBreakpointProperties> {
  // TODO: rework encoding of inline position, introduce enum/class-based external API

  // null - stop at all positions on the line
  // -1 - stop only at the base position (first on the line)
  // 0 or more - index of the lambda on the line to stop at
  // -10 or less - stop only at single conditional return statement (-10 at the base method, (-10-i) at the i-th lambda)
  private Integer encodedInlinePosition = null;

  public static final int COND_RET_CODE = -10;

  @Transient
  public Integer getLambdaOrdinal() {
    if (encodedInlinePosition == null) {
      return null;
    }
    int n = encodedInlinePosition;
    return n <= COND_RET_CODE ? (COND_RET_CODE - n - 1) : n;
  }

  public boolean isConditionalReturn() {
    return encodedInlinePosition != null && encodedInlinePosition <= COND_RET_CODE;
  }

  @OptionTag("lambda-ordinal") // naming is a historic accident
  public Integer getEncodedInlinePosition() {
    return encodedInlinePosition;
  }

  public void setEncodedInlinePosition(Integer inlinePositionEncoded) {
    encodedInlinePosition = inlinePositionEncoded;
  }

  /**
   * @deprecated this method is only for backward compatibility,
   *             use {@link #setEncodedInlinePosition(Integer)}
   */
  @Deprecated(forRemoval = true)
  public void setLambdaOrdinal(Integer lambdaOrdinal) {
    setEncodedInlinePosition(lambdaOrdinal);
  }

  @Override
  public void loadState(@NotNull JavaLineBreakpointProperties state) {
    super.loadState(state);

    encodedInlinePosition = state.encodedInlinePosition;
  }

}
