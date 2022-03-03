// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NonNls private static final String CATEGORY = "category";
  @NonNls private static final String PLUGIN = "plugin";
  @NonNls private static final String IDEA_PLUGIN = "idea-plugin";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String ID = "id";
  @NonNls private static final String DESCRIPTION = "description";
  @NonNls private static final String VERSION = "version";
  @NonNls private static final String VENDOR = "vendor";
  @NonNls private static final String EMAIL = "email";
  @NonNls private static final String URL = "url";
  @NonNls private static final String IDEA_VERSION = "idea-version";
  @NonNls private static final String SINCE_BUILD = "since-build";
  @NonNls private static final String UNTIL_BUILD = "until-build";
  @NonNls private static final String CHANGE_NOTES = "change-notes";
  @NonNls private static final String DEPENDS = "depends";
  @NonNls private static final String DOWNLOADS = "downloads";
  @NonNls private static final String DOWNLOAD_URL = "downloadUrl";
  @NonNls private static final String DOWNLOAD_URL_NEW_STYLE = "download-url";
  @NonNls private static final String SIZE = "size";
  @NonNls private static final String RATING = "rating";
  @NonNls private static final String DATE = "date";
  @NonNls private static final String PLUGIN_UPDATED_DATE = "updatedDate";
  @NonNls private static final String TAGS = "tags";
  @NonNls private static final String PRODUCT_CODE = "productCode";

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

    if (qName.equals(ID)) {
      currentPlugin.setId(currentValueString);
    }
    else if (qName.equals(NAME)) {
      currentPlugin.setName(currentValueString);
    }
    else if (qName.equals(DESCRIPTION)) {
      currentPlugin.setDescription(currentValueString);
    }
    else if (qName.equals(VERSION)) {
      currentPlugin.setVersion(currentValueString);
    }
    else if (qName.equals(VENDOR)) {
      currentPlugin.setVendor(currentValueString);
    }
    else if (qName.equals(DEPENDS)) {
      currentPlugin.addDepends(currentValueString, false);
    }
    else if (qName.equals(CHANGE_NOTES)) {
      currentPlugin.setChangeNotes(currentValueString);
    }
    else if (qName.equals(CATEGORY)) {
      categories.pop();
      categoryName = null;
    }
    else if (qName.equals(RATING)) {
      currentPlugin.setRating(currentValueString);
    }
    else if (qName.equals(DOWNLOAD_URL) || qName.equals(DOWNLOAD_URL_NEW_STYLE)) {
      currentPlugin.setDownloadUrl(currentValueString);
    }
    else if (qName.equals(IDEA_PLUGIN) || qName.equals(PLUGIN)) {
      if (currentPlugin != null) {
        plugins.add(currentPlugin);
      }
      currentPlugin = null;
    }
    else if (qName.equals(TAGS)) {
      currentPlugin.addTags(currentValueString);
    }
    else if (qName.equals(PRODUCT_CODE)) {
      currentPlugin.setProductCode(currentValueString);
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    currentValue.append(ch, start, length);
  }

  @NotNull
  private String buildCategoryName() {
    if (categoryName == null) {
      categoryName = String.join("/", categories);
    }
    return categoryName;
  }
}