/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.openapi.components.ServiceManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public interface XmlRpcServer {
  void addHandler(String name, Object handler);

  boolean hasHandler(String name);

  void removeHandler(String name);

  boolean process(@NotNull String path, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context, @Nullable Map<String, Object> handlers) throws IOException;

  final class SERVICE {
    private SERVICE() {
    }

    public static XmlRpcServer getInstance() {
      return ServiceManager.getService(XmlRpcServer.class);
    }
  }
}