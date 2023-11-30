// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plugin repository XML parser.
 * Supports both updates.xml and plugins.jetbrains.com formats.
 */
final class RepositoryContentHandler extends DefaultHandler {
  private static final @NonNls String CATEGORY = "category";
  private static final @NonNls String PLUGIN = "plugin";
  private static final @NonNls String IDEA_PLUGIN = "idea-plugin";
  private static final @NonNls String NAME = "name";
  private static final @NonNls String ID = "id";
  private static final @NonNls String DESCRIPTION = "description";
  private static final @NonNls String VERSION = "version";
  private static final @NonNls String VENDOR = "vendor";
  private static final @NonNls String EMAIL = "email";
  private static final @NonNls String URL = "url";
  private static final @NonNls String IDEA_VERSION = "idea-version";
  private static final @NonNls String SINCE_BUILD = "since-build";
  private static final @NonNls String UNTIL_BUILD = "until-build";
  private static final @NonNls String CHANGE_NOTES = "change-notes";
  private static final @NonNls String DEPENDS = "depends";
  private static final @NonNls String DOWNLOADS = "downloads";
  private static final @NonNls String DOWNLOAD_URL = "downloadUrl";
  private static final @NonNls String DOWNLOAD_URL_NEW_STYLE = "download-url";
  private static final @NonNls String SIZE = "size";
  private static final @NonNls String RATING = "rating";
  private static final @NonNls String DATE = "date";
  private static final @NonNls String PLUGIN_UPDATED_DATE = "updatedDate";
  private static final @NonNls String TAGS = "tags";
  private static final @NonNls String PRODUCT_CODE = "productCode";

  private final StringBuilder currentValue = new StringBuilder();
  private PluginNode currentPlugin;
  private List<PluginNode> plugins;
  private Stack<String> categories;
  private String categoryName;

  @NotNull
  List<PluginNode> getPluginsList() {
    return plugins == null ? Collections.emptyList() : plugins;
  }

  @Override
  public void startDocument() {
    plugins = new ArrayList<>();
    categories = new Stack<>();
  }

  @Override
  public void startElement(String namespaceURI, String localName, String qName, Attributes attributes) throws SAXException {
    if (qName.equals(CATEGORY)) {
      String category = attributes.getValue(NAME);
      if (!StringUtil.isEmptyOrSpaces(category)) {
        categories.push(category);
        categoryName = null;
      }
    }
    else if (qName.equals(IDEA_PLUGIN)) {
      currentPlugin = new PluginNode(PluginId.getId("unknown"));
      currentPlugin.setCategory(buildCategoryName());
      currentPlugin.setDownloads(attributes.getValue(DOWNLOADS));
      currentPlugin.setSize(attributes.getValue(SIZE));
      currentPlugin.setUrl(attributes.getValue(URL));
      String dateString = attributes.getValue(PLUGIN_UPDATED_DATE) != null ? attributes.getValue(PLUGIN_UPDATED_DATE) : attributes.getValue(DATE);
      if (dateString != null) {
        currentPlugin.setDate(dateString);
      }
      currentPlugin.setIncomplete(false);
    }
    else if (qName.equals(IDEA_VERSION)) {
      currentPlugin.setSinceBuild(attributes.getValue(SINCE_BUILD));
      currentPlugin.setUntilBuild(PluginManager.convertExplicitBigNumberInUntilBuildToStar(attributes.getValue(UNTIL_BUILD)));
    }
    else if (qName.equals(VENDOR)) {
      currentPlugin.setVendorEmail(attributes.getValue(EMAIL));
      currentPlugin.setVendorUrl(attributes.getValue(URL));
    }
    else if (qName.equals(PLUGIN)) {
      String id = attributes.getValue(ID);
      currentPlugin = id == null ? new PluginNode(PluginId.getId("unknown")) : new PluginNode(PluginId.getId(id));
      currentPlugin.setDownloadUrl(attributes.getValue(URL));
      currentPlugin.setVersion(attributes.getValue(VERSION));
      currentPlugin.setIncomplete(true);
    }
    currentValue.setLength(0);
  }

  @Override
  public void endElement(String namespaceURI, String localName, String qName) {
    String currentValueString = currentValue.toString();
    currentValue.setLength(0);

    switch (qName) {
      case ID -> currentPlugin.setId(currentValueString);
      case NAME -> currentPlugin.setName(currentValueString);
      case DESCRIPTION -> currentPlugin.setDescription(currentValueString);
      case VERSION -> currentPlugin.setVersion(currentValueString);
      case VENDOR -> currentPlugin.setVendor(currentValueString);
      case DEPENDS -> currentPlugin.addDepends(currentValueString, false);
      case CHANGE_NOTES -> currentPlugin.setChangeNotes(currentValueString);
      case CATEGORY -> {
        categories.pop();
        categoryName = null;
      }
      case RATING -> currentPlugin.setRating(currentValueString);
      case DOWNLOAD_URL, DOWNLOAD_URL_NEW_STYLE -> currentPlugin.setDownloadUrl(currentValueString);
      case IDEA_PLUGIN, PLUGIN -> {
        if (currentPlugin != null) {
          plugins.add(currentPlugin);
        }
        currentPlugin = null;
      }
      case TAGS -> currentPlugin.addTags(currentValueString);
      case PRODUCT_CODE -> currentPlugin.setProductCode(currentValueString);
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    currentValue.append(ch, start, length);
  }

  private @NotNull String buildCategoryName() {
    if (categoryName == null) {
      categoryName = String.join("/", categories);
    }
    return categoryName;
  }
}