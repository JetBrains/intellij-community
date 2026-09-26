// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.tree.events;

/**
 * The type of modification that happened to a child of a changed parent node.
 */
public enum ChangeInfoKind {
  Added, Removed, Replaced, ContentsChanged
}