// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import org.jetbrains.annotations.NotNull;

/**
 * A pair of ID and type used to serialize tree states
 * @param id the string representing the value of a tree node
 * @param type the string representing the type of a tree node
 */
public record SerializablePathElement(
  @NotNull String id,
  @NotNull String type
) { }
