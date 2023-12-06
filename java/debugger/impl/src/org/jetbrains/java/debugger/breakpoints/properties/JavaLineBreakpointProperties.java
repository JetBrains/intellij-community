// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.debugger.breakpoints.properties;

import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaLineBreakpointProperties extends JavaBreakpointProperties<JavaLineBreakpointProperties> {
  // TODO: rework encoding of inline position, introduce enum/class-based external API

  // null - stop at line and all lambdas
  // -1 - stop only at the base position (first on the line)
  // 0 or more - index of the lambda on the line to stop at
  // -10 or less - stop only at single conditional return statement (-10 at the base method, (-10-i) at the i-th lambda)
  private @Nullable Integer encodedInlinePosition = null;

  private static final int COND_RET_CODE = -10;

  /**
   * Represents a position for breakpoint in the current method (not in lambda).
   * @see #getLambdaOrdinal()
   */
  public static final int NO_LAMBDA = -1;

  /**
   * Encoded inline position for the case when we want to stop on the first statement on the line.
   * @see #encodeInlinePosition
   */
  private static final int BASIC_LINE_POSITION = encodeInlinePosition(NO_LAMBDA, false);


  public static int encodeInlinePosition(int lambdaOrdinal, boolean conditionalReturn) {
    return !conditionalReturn
           ? lambdaOrdinal
           : COND_RET_CODE - lambdaOrdinal - 1;
  }

  /**
   * @return <code>null</code>, if it should suspend on all lambdas and basic line;<br>
   * {@link #NO_LAMBDA}, if it should suspend only on the basic line;<br>
   * positive value, if it should suspend inside the lambda with the ordinal
   */
  @Transient
  public @Nullable Integer getLambdaOrdinal() {
    if (encodedInlinePosition == null) {
      return null;
    }
    int n = encodedInlinePosition;
    return n <= COND_RET_CODE ? (COND_RET_CODE - n - 1) : n;
  }

  public boolean isConditionalReturn() {
    return encodedInlinePosition != null && encodedInlinePosition <= COND_RET_CODE;
  }

  /**
   * @return true iff suspends on the basic line position (including 'all' variants)
   */
  public static boolean isLinePosition(Integer encodedInlinePosition) {
    return encodedInlinePosition == null || encodedInlinePosition == BASIC_LINE_POSITION;
  }

  public boolean isInLambda() {
    Integer lambdaOrdinal = getLambdaOrdinal();
    return lambdaOrdinal != null && lambdaOrdinal != NO_LAMBDA;
  }

  @OptionTag("lambda-ordinal") // naming is a historic accident
  public @Nullable Integer getEncodedInlinePosition() {
    return encodedInlinePosition;
  }

  public void setEncodedInlinePosition(@Nullable Integer inlinePositionEncoded) {
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
