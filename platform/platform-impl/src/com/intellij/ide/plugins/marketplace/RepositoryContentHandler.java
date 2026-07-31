// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.plugins.newui.PluginUiModelBuilder;
import com.intellij.ide.plugins.newui.PluginUiModelBuilderFactory;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNullElse;

/// Plugin repository XML parser. Supports both `updatePlugins.xml` (official) and `updates.xml` (TeamCity) formats.
final class RepositoryContentHandler extends DefaultHandler {
  private static final String CATEGORY = "category";
  private static final String PLUGIN = "plugin";
  private static final String IDEA_PLUGIN = "idea-plugin";
  private static final String NAME = "name";
  private static final String ID = "id";
  private static final String DESCRIPTION = "description";
  private static final String VERSION = "version";
  private static final String VENDOR = "vendor";
  private static final String EMAIL = "email";
  private static final String URL = "url";
  private static final String IDEA_VERSION = "idea-version";
  private static final String SINCE_BUILD = "since-build";
  private static final String UNTIL_BUILD = "until-build";
  private static final String CHANGE_NOTES = "change-notes";
  private static final String DEPENDS = "depends";
  private static final String DOWNLOADS = "downloads";
  private static final String DOWNLOAD_URL = "downloadUrl";
  private static final String DOWNLOAD_URL_NEW_STYLE = "download-url";
  private static final String SIZE = "size";
  private static final String RATING = "rating";
  private static final String DATE = "date";
  private static final String PLUGIN_UPDATED_DATE = "updatedDate";
  private static final String TAGS = "tags";
  private static final String PRODUCT_CODE = "productCode";

  private final StringBuilder currentValue = new StringBuilder();
  private final PluginUiModelBuilderFactory factory;
  private PluginUiModelBuilder builder = null;
  private List<PluginUiModel> plugins;
  private List<String> categories;
  private String categoryName;

  RepositoryContentHandler(@NotNull PluginUiModelBuilderFactory factory) {
    this.factory = factory;
  }

  @NotNull List<PluginUiModel> getPluginsList() {
    return plugins == null ? Collections.emptyList() : plugins;
  }

  @Override
  public void startDocument() {
    plugins = new ArrayList<>();
    categories = new ArrayList<>();
  }

  @Override
  public void startElement(String namespaceURI, String localName, String qName, Attributes attributes) throws SAXException {
    if (qName.equals(CATEGORY)) {
      var category = attributes.getValue(NAME);
      categories.addLast(requireNonNullElse(category, "unknown"));
      categoryName = null;
    }
    else if (qName.equals(IDEA_PLUGIN)) {
      if (builder != null) throw new SAXException("mismatched '" + IDEA_PLUGIN + "' tag");
      builder = factory.createBuilder(PluginId.getId("unknown"))
        .setCategory(buildCategoryName())
        .setDownloads(attributes.getValue(DOWNLOADS))
        .setSize(attributes.getValue(SIZE))
        .setUrl(attributes.getValue(URL));
      var dateString = attributes.getValue(PLUGIN_UPDATED_DATE) != null ? attributes.getValue(PLUGIN_UPDATED_DATE) : attributes.getValue(DATE);
      if (dateString != null) {
        builder.setDate(dateString);
      }
    }
    else if (qName.equals(IDEA_VERSION)) {
      builder()
        .setSinceBuild(attributes.getValue(SINCE_BUILD))
        .setUntilBuild(PluginManager.convertExplicitBigNumberInUntilBuildToStar(attributes.getValue(UNTIL_BUILD)));
    }
    else if (qName.equals(VENDOR)) {
      builder()
        .setVendorEmail(attributes.getValue(EMAIL))
        .setVendorUrl(attributes.getValue(URL));
    }
    else if (qName.equals(PLUGIN)) {
      if (builder != null) throw new SAXException("mismatched '" + PLUGIN + "' tag");
      var id = attributes.getValue(ID);
      builder = factory.createBuilder(PluginId.getId(requireNonNullElse(id, "unknown")))
        .setDownloadUrl(attributes.getValue(URL))
        .setVersion(attributes.getValue(VERSION));
    }
    currentValue.setLength(0);
  }

  @Override
  public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
    var currentValueString = currentValue.toString();
    currentValue.setLength(0);

    switch (qName) {
      case ID -> builder().setId(currentValueString);
      case NAME -> builder().setName(currentValueString);
      case DESCRIPTION -> builder().setDescription(currentValueString);
      case VERSION -> builder().setVersion(currentValueString);
      case VENDOR -> builder().setVendor(currentValueString);
      case DEPENDS -> builder().addDependency(currentValueString, false);
      case CHANGE_NOTES -> builder().setChangeNotes(currentValueString);
      case CATEGORY -> {
        categories.removeLast();
        categoryName = null;
      }
      case RATING -> builder().setRating(currentValueString);
      case DOWNLOAD_URL, DOWNLOAD_URL_NEW_STYLE -> builder().setDownloadUrl(currentValueString);
      case IDEA_PLUGIN, PLUGIN -> {
        builder.setIsFromMarketPlace(true);
        plugins.add(builder.build());
        builder = null;
      }
      case TAGS -> builder().addTag(currentValueString);
      case PRODUCT_CODE -> builder().setProductCode(currentValueString);
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    currentValue.append(ch, start, length);
  }

  private String buildCategoryName() {
    if (categoryName == null) {
      categoryName = String.join("/", categories);
    }
    return categoryName;
  }

  private PluginUiModelBuilder builder() throws SAXException {
    if (builder == null) throw new SAXException("missing '" + PLUGIN + "' or '" + IDEA_PLUGIN + "' tag");
    return builder;
  }
}
