// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.andel {
  requires kotlin.stdlib;
  requires org.jetbrains.annotations;
  requires fleet.util.core;
  requires kotlinx.serialization.core;
  requires kotlinx.serialization.json;
  requires kotlinx.collections.immutable.jvm;
  requires it.unimi.dsi.fastutil;

  exports andel.editor;
  exports andel.intervals;
  exports andel.lines;
  exports andel.operation;
  exports andel.text;
  exports andel.rope;
  exports andel.tokens;
  exports andel.undo;
  exports andel.util;

  opens andel.intervals.impl; // opens for testing. clojure loads into unnamed module, can't explicitly name it here
}