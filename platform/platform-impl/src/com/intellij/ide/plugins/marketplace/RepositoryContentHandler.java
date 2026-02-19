// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.plugins.newui.PluginUiModelBuilder;
import com.intellij.ide.plugins.newui.PluginUiModelBuilderFactory;
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
  private final PluginUiModelBuilderFactory factory;
  private PluginUiModelBuilder builder = null;
  private List<PluginUiModel> plugins;
  private Stack<String> categories;
  private String categoryName;

  RepositoryContentHandler(PluginUiModelBuilderFactory factory) {
    this.factory = factory;
  }

  @NotNull
  List<PluginUiModel> getPluginsList() {
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
      builder = factory.createBuilder(PluginId.getId("unknown"))
        .setCategory(buildCategoryName())
        .setDownloads(attributes.getValue(DOWNLOADS))
        .setSize(attributes.getValue(SIZE))
        .setUrl(attributes.getValue(URL));
      String dateString =
        attributes.getValue(PLUGIN_UPDATED_DATE) != null ? attributes.getValue(PLUGIN_UPDATED_DATE) : attributes.getValue(DATE);
      if (dateString != null) {
        builder.setDate(dateString);
      }
      builder.setIncomplete(false);
    }
    else if (qName.equals(IDEA_VERSION)) {
      builder.setSinceBuild(attributes.getValue(SINCE_BUILD))
        .setUntilBuild(PluginManager.convertExplicitBigNumberInUntilBuildToStar(attributes.getValue(UNTIL_BUILD)));
    }
    else if (qName.equals(VENDOR)) {
      builder.setVendorEmail(attributes.getValue(EMAIL))
        .setVendorUrl(attributes.getValue(URL));
    }
    else if (qName.equals(PLUGIN)) {
      String id = attributes.getValue(ID);
      builder = id == null ? factory.createBuilder(PluginId.getId("unknown")) : factory.createBuilder(PluginId.getId(id));
      builder.setDownloadUrl(attributes.getValue(URL))
        .setVersion(attributes.getValue(VERSION))
        .setIncomplete(true);
    }
    currentValue.setLength(0);
  }

  @Override
  public void endElement(String namespaceURI, String localName, String qName) {
    String currentValueString = currentValue.toString();
    currentValue.setLength(0);

    switch (qName) {
      case ID -> builder.setId(currentValueString);
      case NAME -> builder.setName(currentValueString);
      case DESCRIPTION -> builder.setDescription(currentValueString);
      case VERSION -> builder.setVersion(currentValueString);
      case VENDOR -> builder.setVendor(currentValueString);
      case DEPENDS -> builder.addDependency(currentValueString, false);
      case CHANGE_NOTES -> builder.setChangeNotes(currentValueString);
      case CATEGORY -> {
        categories.pop();
        categoryName = null;
      }
      case RATING -> builder.setRating(currentValueString);
      case DOWNLOAD_URL, DOWNLOAD_URL_NEW_STYLE -> builder.setDownloadUrl(currentValueString);
      case IDEA_PLUGIN, PLUGIN -> {
        if (builder != null) {
          builder.setIsFromMarketPlace(true);
          plugins.add(builder.build());
        }
        builder = null;
      }
      case TAGS -> builder.addTag(currentValueString);
      case PRODUCT_CODE -> builder.setProductCode(currentValueString);
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