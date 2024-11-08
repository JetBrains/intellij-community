// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;

/** Internal API. See a note in {@link MessagePool}. */
@ApiStatus.Internal
public final class IdeaReportingEvent extends IdeaLoggingEvent {
  private final TextBasedThrowable myThrowable;
  private final IdeaPluginDescriptor myPlugin;

  public IdeaReportingEvent(
    @NotNull AbstractMessage messageObject,
    String message,
    @NotNull String stacktrace,
    @Nullable IdeaPluginDescriptor plugin
  ) {
    super(message, null, messageObject);
    myThrowable = new TextBasedThrowable(stacktrace);
    myPlugin = plugin;
  }

  public @Nullable String getOriginalMessage() {
    return getData().getMessage();
  }

  public @NotNull String getOriginalThrowableText() {
    return getData().getThrowableText();
  }

  public @Nullable IdeaPluginDescriptor getPlugin() {
    return myPlugin;
  }

  @Override
  public Throwable getThrowable() {
    return myThrowable;
  }

  @Override
  public @NotNull String getThrowableText() {
    return myThrowable.myStacktrace;
  }

  @Override
  @SuppressWarnings("ConstantConditions")
  public @NotNull AbstractMessage getData() {
    return (AbstractMessage)super.getData();
  }

  private static final class TextBasedThrowable extends Throwable {
    private final String myStacktrace;

    private TextBasedThrowable(String stacktrace) {
      myStacktrace = stacktrace;
    }

    @Override
    public void printStackTrace(PrintWriter s) {
      s.print(myStacktrace);
    }

    @Override
    public void printStackTrace(PrintStream s) {
      s.print(myStacktrace);
    }
  }
}
