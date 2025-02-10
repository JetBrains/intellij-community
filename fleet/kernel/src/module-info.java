// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.kernel {
  requires transitive kotlin.stdlib;
  requires transitive fleet.rhizomedb;
  requires transitive kotlinx.serialization.core;
  requires transitive kotlinx.serialization.json;
  requires transitive fleet.util.core;
  requires transitive fleet.rpc;
  requires kotlinx.coroutines.core;
  requires fleet.preferences;
  requires fleet.reporting.api;
  requires fleet.multiplatform.shims;

  exports fleet.kernel;
  exports fleet.kernel.rebase;
  exports fleet.kernel.rete;
}