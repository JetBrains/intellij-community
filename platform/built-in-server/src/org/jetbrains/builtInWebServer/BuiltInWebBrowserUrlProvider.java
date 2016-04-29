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
package org.jetbrains.builtInWebServer;

import com.intellij.ide.browsers.OpenInBrowserRequest;
import com.intellij.ide.browsers.WebBrowserService;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.lang.Language;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BuiltInWebBrowserUrlProvider extends WebBrowserUrlProvider implements DumbAware {
  @NotNull
  public static List<Url> getUrls(@NotNull VirtualFile file, @NotNull Project project, @Nullable String currentAuthority) {
    if (currentAuthority != null && !compareAuthority(currentAuthority)) {
      return Collections.emptyList();
    }

    String path = WebServerPathToFileManager.getInstance(project).getPath(file);
    if (path == null) {
      return Collections.emptyList();
    }

    int effectiveBuiltInServerPort = BuiltInServerOptions.getInstance().getEffectiveBuiltInServerPort();
    Url url = Urls.newHttpUrl(currentAuthority == null ? "localhost:" + effectiveBuiltInServerPort : currentAuthority, '/' + project.getName() + '/' + path);
    int defaultPort = BuiltInServerManager.getInstance().getPort();
    if (currentAuthority != null || defaultPort == effectiveBuiltInServerPort) {
      return Collections.singletonList(url);
    }
    return Arrays.asList(url, Urls.newHttpUrl("localhost:" + defaultPort, '/' + project.getName() + '/' + path));
  }

  public static boolean compareAuthority(@Nullable String currentAuthority) {
    if (StringUtil.isEmpty(currentAuthority)) {
      return false;
    }

    int portIndex = currentAuthority.indexOf(':');
    if (portIndex < 0) {
      return false;
    }

    String host = currentAuthority.substring(0, portIndex);
    if (!BuiltInWebServerKt.isOwnHostName(host)) {
      return false;
    }

    int port = StringUtil.parseInt(currentAuthority.substring(portIndex + 1), -1);
    return port == BuiltInServerOptions.getInstance().getEffectiveBuiltInServerPort() ||
           port == BuiltInServerManager.getInstance().getPort();
  }

  @Override
  public boolean canHandleElement(@NotNull OpenInBrowserRequest request) {
    if (request.getVirtualFile() instanceof HttpVirtualFile) {
      return true;
    }

    // we must use base language because we serve file - not part of file, but the whole file
    // handlebars, for example, contains HTML and HBS psi trees, so, regardless of context, we should not handle such file
    FileViewProvider viewProvider = request.getFile().getViewProvider();
    return viewProvider.isPhysical() &&
           !(request.getVirtualFile() instanceof LightVirtualFile) &&
           isMyLanguage(viewProvider.getBaseLanguage());
  }

  protected boolean isMyLanguage(@NotNull Language language) {
    return WebBrowserService.isHtmlOrXmlFile(language);
  }

  @Nullable
  @Override
  protected Url getUrl(@NotNull OpenInBrowserRequest request, @NotNull VirtualFile file) throws BrowserException {
    if (file instanceof HttpVirtualFile) {
      return Urls.newFromVirtualFile(file);
    }
    else {
      return ContainerUtil.getFirstItem(getUrls(file, request.getProject(), null));
    }
  }
}
