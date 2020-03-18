// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.io.JsonUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;

/**
 * @author yole
 */
public class PluginRepositoryRequests {
  public static String getBuildForPluginRepositoryRequests() {
    ApplicationInfoEx instance = ApplicationInfoImpl.getShadowInstance();
    String compatibleBuild = PluginManagerCore.getPluginsCompatibleBuild();
    if (compatibleBuild != null) {
      return BuildNumber.fromStringWithProductCode(compatibleBuild, instance.getBuild().getProductCode()).asString();
    }
    return instance.getApiVersion();
  }

  @Nullable
  public static Object getPluginPricesJsonObject() throws IOException {
    ApplicationInfoEx instance = ApplicationInfoImpl.getShadowInstance();
    Url url = Urls.newFromEncoded(instance.getPluginManagerUrl() + "/geo/files/prices");

    return HttpRequests.request(url).throwStatusCodeException(false).productNameAsUserAgent().connect(request -> {
      URLConnection connection = request.getConnection();

      if (connection instanceof HttpURLConnection && ((HttpURLConnection)connection).getResponseCode() != HttpURLConnection.HTTP_OK) {
        return null;
      }

      try (JsonReaderEx json = new JsonReaderEx(FileUtil.loadTextAndClose(request.getReader()))) {
        return JsonUtil.nextAny(json);
      }
    });
  }
}
