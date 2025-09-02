// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.rpc.server {
  requires fleet.rpc;
  requires kotlin.stdlib;
  requires kotlinx.coroutines.core;
  requires kotlinx.serialization.json;
  requires fleet.util.core;
  requires io.opentelemetry.api;
  requires fleet.multiplatform.shims;
  requires io.opentelemetry.context;
  requires fleet.reporting.api;
  requires fleet.reporting.shared;
  exports fleet.rpc.server;
}