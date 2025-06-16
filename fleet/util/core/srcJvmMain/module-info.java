// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.util.core {
  exports fleet.util;
  exports fleet.util.async;
  exports fleet.util.bifurcan;
  exports fleet.util.channels;
  exports fleet.util.openmap;
  exports fleet.util.radixTrie;
  exports fleet.util.reducible;
  exports fleet.util.serialization;
  exports fleet.util.text;
  exports fleet.util.tree;

  requires kotlin.stdlib;
  requires kotlinx.coroutines.core;
  requires java.management;
  requires transitive kotlinx.collections.immutable.jvm;
  requires transitive fleet.util.logging.api;
  requires transitive fleet.fastutil;
  requires transitive fleet.multiplatform.shims;
  requires kotlinx.serialization.core;
  requires kotlinx.serialization.json;
  requires fleet.reporting.shared;
  requires kotlinx.datetime;
  requires fleet.reporting.api;
  requires static fleet.util.multiplatform;
  requires kotlin.codepoints.jvm;
}
