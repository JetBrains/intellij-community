/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.platform.ProjectSetReader;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 *
 * @api {post} /openProjectSet Open project
 * @apiName openProjectSet
 * @apiGroup Platform
 * @apiDescription Checkout a repository from source control and then open an IDEA project from it.
 *
 * @apiParam {Object} vcs The map of the VCS.
 * @apiParam {String} project The path to project to be opened.
 *
 * @apiUse OpenProjectSetRequestExample
 * @apiUse OpenProjectSetRequestExampleMulti
 */
public class ProjectSetRequestHandler extends RestService {
  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.POST;
  }

  @NotNull
  @Override
  protected String getServiceName() {
    return "openProjectSet";
  }

  @Override
  protected boolean isPrefixlessAllowed() {
    return true;
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
    final JsonObject descriptor = new JsonParser().parse(createJsonReader(request)).getAsJsonObject();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        new ProjectSetReader().readDescriptor(descriptor, null);
        activateLastFocusedFrame();
      }
    });
    sendOk(request, context);
    return null;
  }
}
