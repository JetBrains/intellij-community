// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide;

import com.google.gson.stream.JsonReader;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @api {post} /diff The differences between contents
 * @apiName diff
 * @apiGroup Platform
 *
 * @apiParam {String} [fileType] The file type name of the contents (see <a href="https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/fileTypes/FileType.java">FileType.getName()</a>).
 * You can get registered file types using <a href="#api-Platform-about">/rest/about?registeredFileTypes</a> request.
 * @apiParam {String} [windowTitle=Diff Service] The title of the diff window.
 * @apiParam {Boolean} [focused=true] Whether to focus project window.
 *
 * @apiParam {Object[]{2..}} contents The list of the contents to diff.
 * @apiParam {String} [contents.title] The title of the content.
 * @apiParam {String} [contents.fileType] The file type name of the content.
 * @apiParam {String} contents.content The data of the content.
 *
 * @apiUse DiffRequestExample
 */
@SuppressWarnings("HardCodedStringLiteral")
final class DiffHttpService extends RestService {
  @NotNull
  @Override
  protected String getServiceName() {
    return "diff";
  }

  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.POST;
  }

  @Override
  @Nullable
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
    final List<DiffContent> contents = new ArrayList<>();
    final List<String> titles = new ArrayList<>();
    boolean focused = true;
    String windowTitle = null;
    JsonReader reader = createJsonReader(request);
    if (reader.hasNext()) {
      String fileType = null;
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (name.equals("fileType")) {
          fileType = reader.nextString();
        }
        else if (name.equals("focused")) {
          focused = reader.nextBoolean();
        }
        else if (name.equals("windowTitle")) {
          windowTitle = StringUtil.nullize(reader.nextString(), true);
        }
        else if (name.equals("contents")) {
          String error = readContent(reader, contents, titles, fileType);
          if (error != null) {
            return error;
          }
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    if (contents.isEmpty()) {
      return "Empty request";
    }

    Project project = getLastFocusedOrOpenedProject();
    if (project == null) {
      // Argument for @NotNull parameter 'project' of com/intellij/openapi/components/ServiceManager.getService must not be null
      project = ProjectManager.getInstance().getDefaultProject();
    }

    final boolean finalFocused = focused;
    final String finalWindowTitle = windowTitle;
    final Project finalProject = project;
    ApplicationManager.getApplication().invokeLater(() -> {
      if (finalFocused) {
        ProjectUtil.focusProjectWindow(finalProject, true);
      }
      DiffManager.getInstance().showDiff(finalProject, new SimpleDiffRequest(
        StringUtil.notNullize(finalWindowTitle, BuiltInServerBundle.message("dialog.title.diff.service")),
        contents,
        titles
      ));
    }, project.getDisposed());

    sendOk(request, context);
    return null;
  }

  @Nullable
  private static String readContent(@NotNull JsonReader reader, @NotNull List<? super DiffContent> contents, @NotNull List<? super String> titles, @Nullable String defaultFileTypeName) throws IOException {
    FileTypeRegistry fileTypeRegistry = FileTypeRegistry.getInstance();

    FileType defaultFileType = defaultFileTypeName == null ? null : fileTypeRegistry.findFileTypeByName(defaultFileTypeName);
    reader.beginArray();
    while (reader.hasNext()) {
      String title = null;
      String fileType = null;
      String content = null;

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (name.equals("title")) {
          title = reader.nextString();
        }
        else if (name.equals("fileType")) {
          fileType = reader.nextString();
        }
        else if (name.equals("content")) {
          content = reader.nextString();
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();

      if (content == null) {
        return "content is not specified";
      }

      FileType type = fileType == null ? defaultFileType : fileTypeRegistry.findFileTypeByName(fileType);
      contents.add(DiffContentFactory.getInstance().create(content, type));
      titles.add(StringUtil.isEmptyOrSpaces(title) ? "" : title);
    }
    reader.endArray();
    return null;
  }

  @Override
  protected @NotNull OriginCheckResult isOriginAllowed(@NotNull HttpRequest request) {
    return OriginCheckResult.ASK_CONFIRMATION;
  }
}
