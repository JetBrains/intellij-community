// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output;

import com.intellij.build.events.MessageEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public interface BuildOutputParser {
  boolean parse(String line, BuildOutputInstantReader reader, Consumer<MessageEvent> messageConsumer);
}
