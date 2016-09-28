/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide;

import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;

import java.io.IOException;

public class IdeAboutInfoUtil {
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
