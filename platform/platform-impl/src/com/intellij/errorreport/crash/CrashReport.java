/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.errorreport.crash;

import com.google.common.collect.ImmutableSet;
import com.intellij.internal.statistic.analytics.AnalyticsUploader;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CrashReport {
  private static final String PRODUCT_ANDROID_STUDIO = "AndroidStudio"; // must stay in sync with backend registration

  /** {@link Throwable} classes with messages expected to be useful for debugging and not to contain PII. */
  private static final ImmutableSet<Class<? extends Throwable>> THROWABLE_CLASSES_TO_TRACK_MESSAGES = ImmutableSet.of(
    ArrayIndexOutOfBoundsException.class,
    ClassCastException.class,
    ClassNotFoundException.class
  );

  @NotNull public final String productId;
  @Nullable public final String version;
  @NotNull public final String exceptionInfo;

  private CrashReport(@NotNull String productId, @Nullable String version, @NotNull String exceptionInfo) {
    this.productId = productId;
    this.version = version;
    this.exceptionInfo = exceptionInfo;
  }

  public static class Builder {
    private String myProductId = PRODUCT_ANDROID_STUDIO;
    private String myVersion;
    private String myExceptionInfo = "<unknown>";

    private Builder() {
    }

    public Builder setThrowable(@NotNull Throwable t) {
      //noinspection ThrowableResultOfMethodCallIgnored
      myExceptionInfo = getDescription(getRootCause(t));
      return this;
    }

    public Builder setProduct(@NotNull String productId) {
      myProductId = productId;
      return this;
    }

    public Builder setVersion(@NotNull String version) {
      myVersion = version;
      return this;
    }

    public CrashReport build() {
      return new CrashReport(myProductId, myVersion, myExceptionInfo);
    }

    public static Builder createForException(@NotNull Throwable t) {
      return new Builder().setThrowable(t);
    }
  }

  // Similar to ExceptionUntil.getRootCause, but attempts to avoid infinite recursion
  @NotNull
  public static Throwable getRootCause(@NotNull Throwable t) {
    int depth = 0;
    while (depth++ < 20) {
      if (t.getCause() == null) return t;
      t = t.getCause();
    }
    return t;
  }

  /**
   * Returns an exception description (similar to {@link ExceptionUtil#getThrowableText(Throwable)} with the exception message
   * removed in order to strip off any PII. The exception message is include for some specific exceptions where we know that the
   * message will not have any PII.
   */
  @NotNull
  public static String getDescription(@NotNull Throwable t) {
    if (THROWABLE_CLASSES_TO_TRACK_MESSAGES.contains(t.getClass())) {
      return ExceptionUtil.getThrowableText(t);
    }

    StringBuilder sb = new StringBuilder(256);

    sb.append(t.getClass().getName());
    sb.append(": <elided>\n"); // note: some message is needed for the backend to parse the report properly

    for (StackTraceElement el : t.getStackTrace()) {
      sb.append("\tat ");
      sb.append(el);
      sb.append('\n');
    }

    return sb.toString();
  }
}
