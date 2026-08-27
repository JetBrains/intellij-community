// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output;

import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FileMessageEventImpl;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class GroovycOutputParser implements BuildOutputParser {
  private static final Pattern FILE_LINE = Pattern.compile("(.+\\.groovy): (\\d+): (.*)");
  private static final Pattern LOCATION = Pattern.compile("@ line (\\d+), column (\\d+)\\.");

  @Override
  public boolean parse(@NlsSafe @NotNull String line,
                       @NotNull BuildOutputInstantReader reader,
                       @NotNull Consumer<? super BuildEvent> messageConsumer) {
    Matcher head = FILE_LINE.matcher(line);
    if (!head.matches()) {
      return false;
    }

    Path path;
    try {
      path = Path.of(head.group(1));
    }
    catch (InvalidPathException e) {
      return false;
    }
    if (!Files.isRegularFile(path)) {
      return false;
    }

    String rest = head.group(3);
    Matcher location = LOCATION.matcher(rest);
    String message;
    String detailedMessage;
    if (location.find()) {
      message = rest.substring(0, location.start()).trim();
      detailedMessage = line;
    }
    else {
      String next = reader.readLine();
      if (next == null) {
        return false;
      }
      location = LOCATION.matcher(next);
      if (!location.find()) {
        reader.pushBack();
        return false;
      }
      message = rest.trim();
      detailedMessage = line + "\n" + next;
    }

    try {
      int row = Integer.parseInt(head.group(2));
      int column = Integer.parseInt(location.group(2));
      messageConsumer.accept(new FileMessageEventImpl(null, reader.getParentEventId(), null, message, null, detailedMessage,
                                                      MessageEvent.Kind.ERROR, LangBundle.message("build.event.title.compiler"), null,
                                                      new FilePosition(path, row - 1, column - 1)));
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }
}