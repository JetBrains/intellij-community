// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon;

import com.google.common.base.MoreObjects;
import com.google.protobuf.Message;
import com.intellij.execution.process.mediator.rpc.ProcessMediatorProto;
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

import java.util.Arrays;
import java.util.List;

public class ProcessMediatorDaemonRuntimeClasspath {

  private static final Class<?>[] CLASSPATH_CLASSES = {
    ProcessMediatorDaemonMainKt.class,
    ProcessMediatorProto.class,

    KotlinVersion.class, // kotlin-stdlib
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
  };

  public static @NotNull Class<?> getMainClass() {
    return ProcessMediatorDaemonMainKt.class;
  }

  public static @NotNull List<@NotNull Class<?>> getClasspathClasses() {
    return Arrays.asList(CLASSPATH_CLASSES);
  }
}
