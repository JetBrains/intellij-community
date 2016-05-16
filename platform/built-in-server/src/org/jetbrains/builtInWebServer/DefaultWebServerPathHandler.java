/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.builtInWebServer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.io.Responses;

import java.io.File;

import static org.jetbrains.builtInWebServer.BuiltInWebServer.canBeAccessedDirectly;

final class DefaultWebServerPathHandler extends WebServerPathHandler {
  @Override
  public boolean process(@NotNull String path,
                         @NotNull Project project,
                         @NotNull FullHttpRequest request,
                         @NotNull ChannelHandlerContext context,
                         @Nullable String projectName,
                         @NotNull String decodedRawPath,
                         boolean isCustomHost) {
    Channel channel = context.channel();
    WebServerPathToFileManager pathToFileManager = WebServerPathToFileManager.getInstance(project);
    VirtualFile result = pathToFileManager.pathToFileCache.getIfPresent(path);
    boolean indexUsed = false;
    if (result == null || !result.isValid()) {
      result = pathToFileManager.findByRelativePath(project, path);
      if (result == null) {
        if (path.isEmpty()) {
          Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, request);
          return true;
        }
        else {
          return false;
        }
      }
      else if (result.isDirectory()) {
        result = BuiltInWebServer.findIndexFile(result);
        if (result == null) {
          Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, request);
          return true;
        }

        if (!endsWithSlash(decodedRawPath)) {
          redirectToDirectory(request, channel, isCustomHost ? path : (projectName + '/' + path));
          return true;
        }

        indexUsed = true;
      }

      pathToFileManager.pathToFileCache.put(path, result);
    }

    if (NettyUtil.origin(request) == null &&
        NettyUtil.referrer(request) == null &&
        NettyUtil.isRegularBrowser(request) &&
        !canBeAccessedDirectly(result.getName())) {
      Responses.sendStatus(HttpResponseStatus.NOT_FOUND, context.channel(), request);
      return true;
    }

    if (!indexUsed && !path.endsWith(result.getName())) {
      if (endsWithSlash(decodedRawPath)) {
        indexUsed = true;
      }
      else {
        // FallbackResource feature in action, /login requested, /index.php retrieved, we must not redirect /login to /login/
        if (path.endsWith(result.getParent().getName())) {
          redirectToDirectory(request, channel, isCustomHost ? path : (projectName + '/' + path));
          return true;
        }
      }
    }

    PathInfo root = WebServerPathToFileManager.getInstance(project).getRoot(result);
    if (!checkAccess(result, root.getRoot(), channel, request)) {
      return true;
    }

    StringBuilder canonicalRequestPath = new StringBuilder();
    canonicalRequestPath.append('/');
    if (!isCustomHost) {
      canonicalRequestPath.append(projectName).append('/');
    }
    canonicalRequestPath.append(path);
    if (indexUsed) {
      canonicalRequestPath.append('/').append(result.getName());
    }

    for (WebServerFileHandler fileHandler : WebServerFileHandler.EP_NAME.getExtensions()) {
      try {
        if (fileHandler.process(result, canonicalRequestPath, project, request, channel, isCustomHost)) {
          return true;
        }
      }
      catch (Throwable e) {
        BuiltInWebServer.LOG.error(e);
      }
    }
    return false;
  }

  private static boolean checkAccess(VirtualFile virtualFile, VirtualFile root, Channel channel, HttpRequest request) {
    File ioFile = VfsUtilCore.virtualToIoFile(virtualFile);

    if (virtualFile.isInLocalFileSystem()) {
      if (virtualFile.isDirectory()) {
        Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, request);
        return false;
      }
      else if (!BuiltInWebServer.StaticFileHandler.checkAccess(channel, ioFile, request, VfsUtilCore.virtualToIoFile(root))) {
        return false;
      }
    }
    else if (virtualFile.is(VFileProperty.HIDDEN)) {
      Responses.sendStatus(HttpResponseStatus.NOT_FOUND, channel, request);
      return false;
    }

    return true;
  }
}