// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;

public final class IdeaReportingEvent extends IdeaLoggingEvent {
  private final TextBasedThrowable myThrowable;
  private final IdeaPluginDescriptor myPlugin;

  public IdeaReportingEvent(@NotNull AbstractMessage messageObject, String message, @NotNull String stacktrace, IdeaPluginDescriptor plugin) {
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
  public String getThrowableText() {
    return myThrowable.myStacktrace;
  }

  @Override
  @SuppressWarnings("ConstantConditions")
  public @NotNull AbstractMessage getData() {
    return (AbstractMessage)super.getData();
  }

  static class TextBasedThrowable extends Throwable {
    private final String myStacktrace;

    TextBasedThrowable(String stacktrace) {
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