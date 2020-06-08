// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;

import java.io.IOException;

public final class IdeAboutInfoUtil {
  public static void writeAboutJson(JsonWriter writer) throws IOException {
    String appName = ApplicationInfoEx.getInstanceEx().getFullApplicationName();
    BuildNumber build = ApplicationInfo.getInstance().getBuild();

    if (!PlatformUtils.isIdeaUltimate()) {
      String productName = ApplicationNamesInfo.getInstance().getProductName();
      appName = appName.replace(productName + " (" + productName + ")", productName);
      appName = StringUtil.trimStart(appName, "JetBrains ");
    }

    writer.name("name").value(appName);
    writer.name("productName").value(ApplicationNamesInfo.getInstance().getProductName());
    writer.name("baselineVersion").value(build.getBaselineVersion());
    if (!build.isSnapshot()) {
      writer.name("buildNumber").value(build.asStringWithoutProductCode());
    }
  }
}
