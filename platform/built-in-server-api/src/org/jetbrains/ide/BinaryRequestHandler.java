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
package org.jetbrains.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public abstract class BinaryRequestHandler {
  public static final ExtensionPointName<BinaryRequestHandler> EP_NAME = ExtensionPointName.create("org.jetbrains.binaryRequestHandler");

  @NotNull
  /**
   * uuidgen on Mac OS X could be used to generate UUID
   */
  public abstract UUID getId();

  @NotNull
  public abstract ChannelHandler getInboundHandler(@NotNull ChannelHandlerContext context);
}