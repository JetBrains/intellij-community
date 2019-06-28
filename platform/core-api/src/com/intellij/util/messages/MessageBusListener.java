// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages;

import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public interface MessageBusListener<L> {
  @NotNull Topic<L> getTopic();
  @NotNull L getListener();
}
