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
  exports com.tylerthrailkill.helpers.prettyprint;
  exports fleet.util.fastutil;
  exports fleet.util.fastutil.ints;
  exports fleet.util.fastutil.longs;

  requires kotlin.stdlib;
  requires kotlinx.coroutines.core;
  requires kotlinx.coroutines.slf4j;
  requires transitive kotlinx.collections.immutable.jvm;
  requires transitive fleet.util.logging.api;
  requires kotlinx.serialization.core;
  requires kotlinx.serialization.json;
  requires kotlinx.datetime;
  requires fleet.preferences;
  requires fleet.reporting.api;
  requires fleet.util.os;
}
