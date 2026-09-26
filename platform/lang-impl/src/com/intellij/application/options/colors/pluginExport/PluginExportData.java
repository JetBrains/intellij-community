// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.pluginExport;

import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;

public final class PluginExportData {
  private static final String DESCRIPTION_PROPERTY = "description";
  private static final String VENDOR_NAME_PROPERTY = "vendorName";
  private static final String VENDOR_MAIL_PROPERTY = "vendorMail";
  private static final String VENDOR_URL_PROPERTY = "vendorUrl";
  private static final String VERSION_PROPERTY = "version";

  private String myDescription;
  private String myVendorName;
  private String myVendorMail;
  private String myVendorUrl;

  private String myPluginVersion;
  private String myChangeNotes;


  public PluginExportData(@NotNull Properties info) {
    initData(info);
  }

  private void initData(@NotNull Properties info) {
    myDescription = info.getProperty(DESCRIPTION_PROPERTY);
    myVendorName = info.getProperty(VENDOR_NAME_PROPERTY);
    myVendorMail = info.getProperty(VENDOR_MAIL_PROPERTY);
    myVendorUrl = info.getProperty(VENDOR_URL_PROPERTY);
    myPluginVersion = info.getProperty(VERSION_PROPERTY);
  }

  public String getDescription() {
    return normalize(myDescription, "");
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public String getVendorName() {
    return normalize(myVendorName, "");
  }

  public void setVendorName(String vendorName) {
    myVendorName = vendorName;
  }

  public String getVendorMail() {
    return normalize(myVendorMail, "");
  }

  public void setVendorMail(String vendorMail) {
    myVendorMail = vendorMail;
  }

  public String getVendorUrl() {
    return normalize(myVendorUrl, "http://");
  }

  public void setVendorUrl(String vendorUrl) {
    myVendorUrl = vendorUrl;
  }

  public String getPluginVersion() {
    return normalize(myPluginVersion, "0.1");
  }

  public void setPluginVersion(String pluginVersion) {
    myPluginVersion = pluginVersion;
  }

  public String getChangeNotes() {
    return normalize(myChangeNotes, "");
  }

  public void setChangeNotes(String changeNotes) {
    myChangeNotes = changeNotes;
  }

  public String getSinceBuild() {
    return AbstractColorsScheme.CURR_VERSION + ".0";
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

  public void saveToProperties(@NotNull Properties properties) {
    properties.setProperty(DESCRIPTION_PROPERTY, getDescription());
    properties.setProperty(VERSION_PROPERTY, getPluginVersion());
    properties.setProperty(VENDOR_URL_PROPERTY, getVendorUrl());
    properties.setProperty(VENDOR_MAIL_PROPERTY, getVendorMail());
    properties.setProperty(VENDOR_NAME_PROPERTY, getVendorName());
  }
}
