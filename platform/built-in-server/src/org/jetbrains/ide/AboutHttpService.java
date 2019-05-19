// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide;

import com.google.gson.stream.JsonWriter;
import com.intellij.ide.IdeAboutInfoUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @api {get} /about The application info
 * @apiName about
 * @apiGroup Platform
 *
 * @apiParam {Boolean} [registeredFileTypes=false] Whether to include the list of registered file types.
 * @apiParam {Boolean} [more=false] Whether to include the full info.
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
public class AboutHttpService extends RestService {
  @NotNull
  @Override
  protected String getServiceName() {
    return "about";
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
    BufferExposingByteArrayOutputStream byteOut = new BufferExposingByteArrayOutputStream();
    getAbout(byteOut, urlDecoder);
    send(byteOut, request, context);
    return null;
  }

  public static void getAbout(@NotNull OutputStream out, @Nullable QueryStringDecoder urlDecoder) throws IOException {
    JsonWriter writer = createJsonWriter(out);
    writer.beginObject();

    IdeAboutInfoUtil.writeAboutJson(writer);

    if (urlDecoder != null && getBooleanParameter("registeredFileTypes", urlDecoder)) {
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

    if (urlDecoder != null && getBooleanParameter("more", urlDecoder)) {
      ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
      writer.name("vendor").value(appInfo.getCompanyName());
      writer.name("isEAP").value(appInfo.isEAP());
      writer.name("productCode").value(appInfo.getBuild().getProductCode());
      writer.name("buildDate").value(appInfo.getBuildDate().getTime().getTime());
      writer.name("isSnapshot").value(appInfo.getBuild().isSnapshot());
      writer.name("configPath").value(PathManager.getConfigPath());
      writer.name("systemPath").value(PathManager.getSystemPath());
      writer.name("binPath").value(PathManager.getBinPath());
      writer.name("logPath").value(PathManager.getLogPath());
      writer.name("homePath").value(PathManager.getHomePath());
    }

    writer.endObject();
    writer.close();
  }
}
