/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.colors.pluginExport;

import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;

public class PluginExportData {
  private final static String DESCRIPTION_PROPERTY = "description";
  private final static String VENDOR_NAME_PROPERTY = "vendorName";
  private final static String VENDOR_MAIL_PROPERTY = "vendorMail";
  private final static String VENDOR_URL_PROPERTY = "vendorUrl";
  private final static String VERSION_PROPERTY = "version";

  private String myDescription;
  private String myVendorName;
  private String myVendorMail;
  private String myVendorUrl;

  private String myPluginVersion;
  private String myChangeNotes;

  private final Properties myMetaInfo;

  public PluginExportData(@NotNull Properties info) {
    myMetaInfo = info;
    initData();
  }

  private void initData() {
    myDescription = myMetaInfo.getProperty(DESCRIPTION_PROPERTY);
    myVendorName = myMetaInfo.getProperty(VENDOR_NAME_PROPERTY);
    myVendorMail = myMetaInfo.getProperty(VENDOR_MAIL_PROPERTY);
    myVendorUrl = myMetaInfo.getProperty(VENDOR_URL_PROPERTY);
    myPluginVersion = myMetaInfo.getProperty(VERSION_PROPERTY);
  }

  public String getDescription() {
    return normalize(myDescription, "");
  }

  public void setDescription(String description) {
    myDescription = description;
    myMetaInfo.setProperty(DESCRIPTION_PROPERTY, myDescription);
  }

  public String getVendorName() {
    return normalize(myVendorName, "");
  }

  public void setVendorName(String vendorName) {
    myVendorName = vendorName;
    myMetaInfo.setProperty(VENDOR_NAME_PROPERTY, vendorName);
  }

  public String getVendorMail() {
    return normalize(myVendorMail, "");
  }

  public void setVendorMail(String vendorMail) {
    myVendorMail = vendorMail;
    myMetaInfo.setProperty(VENDOR_MAIL_PROPERTY, vendorMail);
  }

  public String getVendorUrl() {
    return normalize(myVendorUrl, "http://");
  }

  public void setVendorUrl(String vendorUrl) {
    myVendorUrl = vendorUrl;
    myMetaInfo.setProperty(VENDOR_URL_PROPERTY, vendorUrl);
  }

  public String getPluginVersion() {
    return normalize(myPluginVersion, "0.1");
  }

  public void setPluginVersion(String pluginVersion) {
    myPluginVersion = pluginVersion;
    myMetaInfo.setProperty(VERSION_PROPERTY, pluginVersion);
  }

  public String getChangeNotes() {
    return normalize(myChangeNotes, "");
  }

  public void setChangeNotes(String changeNotes) {
    myChangeNotes = changeNotes;
  }

  public String getSinceBuild() {
    return Integer.toString(AbstractColorsScheme.CURR_VERSION) + ".0";
  }

  private static String normalize(@Nullable String value, @NotNull String defaultValue) {
    return value == null || StringUtil.isEmptyOrSpaces(value) ? defaultValue : value.trim();
  }

  public boolean isEmpty() {
    return
      myDescription == null &&
      myPluginVersion == null &&
      myVendorUrl == null &&
      myVendorMail == null &&
      myVendorName == null;
  }
}
