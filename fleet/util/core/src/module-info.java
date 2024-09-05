// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.util.core {
  exports fleet.util;
  exports fleet.util.async;
  exports fleet.util.bifurcan;
  exports fleet.util.channels;
  exports fleet.util.openmap;
  exports fleet.util.serialization;
  exports fleet.util.tree;
  exports com.tylerthrailkill.helpers.prettyprint;

  requires kotlin.stdlib;
  requires kotlinx.coroutines.core;
  requires kotlinx.coroutines.slf4j;
  requires transitive kotlinx.collections.immutable.jvm;
  requires bifurcan;
  requires transitive fleet.util.logging.api;
  requires kotlinx.serialization.core;
  requires kotlinx.serialization.json;
  requires fleet.preferences;
  requires fleet.reporting.api;
  requires fleet.util.os;
}
