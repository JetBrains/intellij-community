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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class HttpRequestHandler {
  // Your handler will be instantiated on first user request
  public static final ExtensionPointName<HttpRequestHandler> EP_NAME = ExtensionPointName.create("com.intellij.httpRequestHandler");

  public boolean isSupported(FullHttpRequest request) {
    return request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD;
  }

  public abstract boolean process(QueryStringDecoder urlDecoder, FullHttpRequest request, ChannelHandlerContext context)
    throws IOException;

  protected static boolean checkPrefix(@NotNull String uri, @NotNull String prefix) {
    if (uri.length() > prefix.length() && uri.charAt(0) == '/' && uri.regionMatches(true, 1, prefix, 0, prefix.length())) {
      if ((uri.length() - prefix.length()) == 1) {
        return true;
      }
      else {
        char c = uri.charAt(prefix.length() + 1);
        return c == '/' || c == '?';
      }
    }
    return false;
  }
}
