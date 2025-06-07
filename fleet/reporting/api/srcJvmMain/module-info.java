// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.reporting.api {
  requires kotlin.stdlib;
  requires kotlinx.coroutines.core;
  requires io.opentelemetry.api;
  requires io.opentelemetry.context;

  exports fleet.tracing;
  exports fleet.tracing.runtime;
}