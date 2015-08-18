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

import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.PlatformUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @api {get} /about The application info
 * @apiName about
 * @apiGroup Platform
 *
 * @apiParam {Boolean} [registeredFileTypes=false] Whether to include the list of registered file types.
 *
 * @apiSuccess {String} name The full application name.
 * @apiSuccess {String} productName The product name.
 * @apiSuccess {String} baselineVersion The baseline version.
 * @apiSuccess {String} [buildNumber] The build number.
 *
 * @apiSuccess {Object[]} registeredFileTypes The list of registered file types.
 * @apiSuccess {String} registeredFileTypes.name The name of file type.
 * @apiSuccess {String} registeredFileTypes.description The user-readable description of the file type.
 * @apiSuccess {Boolean} registeredFileTypes.isBinary Whether files of the specified type contain binary data.
 *
 *  * @apiExample Request-Example:
 * /rest/about?registeredFileTypes
 *
 * @apiUse SuccessExample
 * @apiUse SuccessExampleWithRegisteredFileTypes
 */
class AboutHttpService extends RestService {
  @NotNull
  @Override
  protected String getServiceName() {
    return "about";
  }

  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.GET;
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
    BuildNumber build = ApplicationInfo.getInstance().getBuild();
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    BufferExposingByteArrayOutputStream byteOut = new BufferExposingByteArrayOutputStream();
    JsonWriter writer = createJsonWriter(byteOut);
    writer.beginObject();

    String appName = ApplicationInfoEx.getInstanceEx().getFullApplicationName();
    if (!PlatformUtils.isIdeaUltimate()) {
      String productName = ApplicationNamesInfo.getInstance().getProductName();
      appName = appName.replace(productName + " (" + productName + ")", productName);
      if (appName.startsWith("JetBrains ")) {
        appName = appName.substring("JetBrains ".length());
      }
    }

    writer.name("name").value(appName);
    writer.name("productName").value(ApplicationNamesInfo.getInstance().getProductName());
    writer.name("baselineVersion").value(build.getBaselineVersion());
    if (build.getBuildNumber() != Integer.MAX_VALUE) {
      writer.name("buildNumber").value(build.getBuildNumber());
    }

    if (getBooleanParameter("registeredFileTypes", urlDecoder)) {
      writer.name("registeredFileTypes").beginArray();
      for (FileType fileType : FileTypeRegistry.getInstance().getRegisteredFileTypes()) {
        writer.beginObject();
        writer.name("name").value(fileType.getName());
        writer.name("description").value(fileType.getDescription());
        writer.name("isBinary").value(fileType.isBinary());
        writer.endObject();
      }
      writer.endArray();
    }

    writer.endObject();
    writer.close();
    send(byteOut, request, context);
    return null;
  }

  @Override
  protected boolean activateToolBeforeExecution() {
    return false;
  }
}
