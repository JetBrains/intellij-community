// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface ConfigurableWithId extends Configurable {

  /**
   * Unique configurable id.
   * Note this id should be THE SAME as the one specified in XML.
   *
   * @see ConfigurableEP#id
   */
  @NotNull
  @NonNls
  String getId();
}
