// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface XmlRpcServer {
  void addHandler(String name, Object handler);

  boolean hasHandler(String name);

  void removeHandler(String name);

  boolean process(@NotNull String path, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context, @Nullable Map<String, Object> handlers);

  final class SERVICE {
    private SERVICE() {
    }

    public static XmlRpcServer getInstance() {
      return ApplicationManager.getApplication().getService(XmlRpcServer.class);
    }
  }
}