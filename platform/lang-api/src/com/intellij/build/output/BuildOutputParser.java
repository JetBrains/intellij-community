// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.util.NlsSafe;

import java.util.function.Consumer;

/**
 * Parses output messages and creates build events from those.
 */
public interface BuildOutputParser {
  /**
   * Parses output line.
   *
   * @param line            is line to parse
   * @param reader          is source reader of {@code line}.
   * @param messageConsumer is consumer of parsed build events.
   * @return true if line is successfully parsed
   */
  boolean parse(@NlsSafe String line, BuildOutputInstantReader reader, Consumer<? super BuildEvent> messageConsumer);
}
