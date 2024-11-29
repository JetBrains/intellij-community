// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
module fleet.rpc {
  requires fleet.util.core;
  requires kotlinx.serialization.core;
  requires kotlinx.serialization.json;
  requires kotlin.stdlib;
  requires kotlin.reflect;
  requires io.opentelemetry.context;
  requires kotlinx.coroutines.core;
  requires java.net.http;
  requires org.jetbrains.annotations;
  requires io.opentelemetry.api;
  requires fleet.preferences;
  requires fleet.reporting.api;

  exports fleet.rpc;
  exports fleet.rpc.core;
  exports fleet.rpc.core.util;
  exports fleet.rpc.client;
  exports fleet.rpc.client.proxy;
}