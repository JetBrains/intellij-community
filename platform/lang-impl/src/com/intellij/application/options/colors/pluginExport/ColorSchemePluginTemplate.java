// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.pluginExport;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import org.jetbrains.annotations.NotNull;

public final class ColorSchemePluginTemplate {
  public static final String PLUGIN_ID = "%PLUGIN_ID%";
  public static final String DISPLAY_NAME = "%DISPLAY_NAME%";

  public static final String VENDOR_NAME = "%VENDOR_NAME%";
  public static final String VENDOR_MAIL = "%VENDOR_MAIL%";
  public static final String VENDOR_URL = "%VENDOR_URL%";

  public static final String PLUGIN_VERSION = "%VERSION%";
  public static final String PLUGIN_DESCRIPTION = "%DESCRIPTION%";
  public static final String CHANGE_NOTES = "%NOTES%";
  public static final String SINCE_BUILD = "%SINCE_BUILD%";

  public static final String SCHEME_NAME = "$SCHEME_NAME";
  private static final String TEMPLATE_TEXT =
    "<idea-plugin>\n" +
    "  <id>" + PLUGIN_ID + "</id>\n" +
    "  <name>" + DISPLAY_NAME + "</name>\n" +
    "  <version>" + PLUGIN_VERSION + "</version>\n" +
    "  <vendor email=\"" + VENDOR_MAIL + "\" url=\"" + VENDOR_URL + "\">" + VENDOR_NAME + "</vendor>\n" +
    "\n" +
    "  <description><![CDATA[\n" +
    "      " + PLUGIN_DESCRIPTION + "\n" +
    "    ]]></description>\n" +
    "\n" +
    "  <change-notes><![CDATA[\n" +
    "      " + CHANGE_NOTES + "\n" +
    "    ]]>\n" +
    "  </change-notes>\n" +
    "\n" +
    "  <idea-version since-build=\"" + SINCE_BUILD + "\"/>\n" +
    "\n" +
    "  <depends>com.intellij.modules.lang</depends>\n" +
    "\n" +
    "  <extensions defaultExtensionNs=\"com.intellij\">\n" +
    "    <bundledColorScheme path=\"/colors/" + SCHEME_NAME + "\"/>\n" +
    "  </extensions>\n" +
    "</idea-plugin>";

  private String myText;

  public ColorSchemePluginTemplate(@NotNull EditorColorsScheme scheme, @NotNull PluginExportData exportData) {
    myText = TEMPLATE_TEXT;
    initTemplate(scheme, exportData);
  }
  
  private void initTemplate(@NotNull EditorColorsScheme scheme, @NotNull PluginExportData exportData) {
    myText = myText.replace(PLUGIN_ID, "color.scheme." + scheme.getName());
    myText = myText.replace(DISPLAY_NAME, scheme.getName() + " Color Scheme");

    myText = myText.replace(VENDOR_NAME, exportData.getVendorName());
    myText = myText.replace(VENDOR_MAIL, exportData.getVendorMail());
    myText = myText.replace(VENDOR_URL, exportData.getVendorUrl());

    myText = myText.replace(PLUGIN_VERSION, exportData.getPluginVersion());
    myText = myText.replace(PLUGIN_DESCRIPTION, exportData.getDescription());
    myText = myText.replace(CHANGE_NOTES, exportData.getChangeNotes());
    myText = myText.replace(SINCE_BUILD,  exportData.getSinceBuild());

    myText = myText.replace(SCHEME_NAME, scheme.getName());
  }
  

  public String getText() {
    return myText;
  }
}
