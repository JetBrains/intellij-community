// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.platform.ProjectSetReader;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
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
final class ProjectSetRequestHandler extends RestService {
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
    final JsonObject descriptor = JsonParser.parseReader(createJsonReader(request)).getAsJsonObject();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      new ProjectSetReader().readDescriptor(descriptor, null);
      activateLastFocusedFrame();
    });
    sendOk(request, context);
    return null;
  }

  @Override
  public boolean isAccessible(@NotNull HttpRequest request) {
    return true;
  }
}
