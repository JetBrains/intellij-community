// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.rhizomedb {
  requires fleet.util.core;
  requires kotlin.stdlib;
  requires kotlinx.collections.immutable.jvm;
  requires transitive kotlinx.serialization.core;
  requires fleet.multiplatform.shims;
  requires kotlinx.serialization.json;
  requires org.jetbrains.annotations;
  exports com.jetbrains.rhizomedb;
  exports com.jetbrains.rhizomedb.impl;
  exports com.jetbrains.rhizomedb.rql;
}