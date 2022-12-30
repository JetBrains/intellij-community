// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon;

import com.google.common.base.MoreObjects;
import com.google.protobuf.Message;
import com.intellij.execution.process.mediator.rpc.ProcessMediatorProto;
import com.sun.jna.Native;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.internal.ServerImpl;
import io.grpc.kotlin.AbstractCoroutineStub;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.grpc.netty.shaded.io.netty.resolver.AddressResolverGroup;
import io.grpc.netty.shaded.io.netty.util.NetUtil;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.lite.ProtoLiteUtils;
import io.grpc.stub.AbstractStub;
import io.perfmark.PerfMark;
import kotlin.KotlinVersion;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.future.FutureKt;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"RedundantArrayCreation", "KotlinInternalInJava", "UnnecessaryFullyQualifiedName"})
public class DaemonProcessRuntimeClasspath {

  private static final List<Class<?>> CLASSPATH_CLASSES = List.of(new Class<?>[]{
    DaemonProcessMainKt.class,
    DaemonLaunchOptions.class,
    ProcessMediatorProto.class,

    KotlinVersion.class, // kotlin-stdlib
    kotlin.internal.jdk8.JDK8PlatformImplementations.class, // kotlin-stdlib-jdk8
    kotlin.internal.jdk7.JDK7PlatformImplementations.class, // kotlin-stdlib-jdk7
    CoroutineScope.class, // kotlinx-coroutines-core
    FutureKt.class, // kotlinx-coroutines-jdk8

    Grpc.class, // grpc-api
    ServerImpl.class, // grpc-core
    ProtoUtils.class, // grpc-protobuf
    ProtoLiteUtils.class, // grpc-protobuf-lite
    Context.class, // grpc-context
    AbstractStub.class, // grpc-stub
    AbstractCoroutineStub.class, // grpc-kotlin-stub
    MoreObjects.class, // guava

    Message.class, // protobuf
    NetUtil.class, // netty common
    EventLoopGroup.class, // netty transport
    AddressResolverGroup.class, // netty resolver
    ByteBufAllocator.class, // netty buffer
    ProtobufDecoder.class, // netty codec
    PerfMark.class, // perfmark-api

    Native.class, // JNA
  });

  private static final List<String> PROPERTY_NAMES = List.of(new String[]{
    "java.net.preferIPv4Stack",
    "java.net.preferIPv6Addresses",
    "java.util.logging.config.file",
    "jna.boot.library.path",
  });

  public static @NotNull Class<?> getMainClass() {
    return DaemonProcessMainKt.class;
  }

  public static @NotNull List<@NotNull Class<?>> getClasspathClasses() {
    return CLASSPATH_CLASSES;
  }

  public static @NotNull Map<@NotNull String, @NotNull String> getProperties() {
    LinkedHashMap<String, String> properties = new LinkedHashMap<>();
    for (String name : PROPERTY_NAMES) {
      String property = System.getProperty(name);
      if (property != null) {
        properties.put(name, property);
      }
    }
    return properties;
  }
}
