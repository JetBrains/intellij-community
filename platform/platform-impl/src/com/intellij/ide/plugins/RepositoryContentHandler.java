/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.Collections;
import java.util.List;

/**
 * Plugin repository XML parser.
 * Supports both updates.xml and plugins.jetbrains.com formats.
 */
class RepositoryContentHandler extends DefaultHandler {
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

  private final StringBuilder currentValue = new StringBuilder();
  private PluginNode currentPlugin;
  private List<IdeaPluginDescriptor> plugins;
  private Stack<String> categories;
  private String categoryName;

  @NotNull
  public List<IdeaPluginDescriptor> getPluginsList() {
    return plugins != null ? plugins : Collections.<IdeaPluginDescriptor>emptyList();
  }

  @Override
  public void startDocument() throws SAXException {
    plugins = ContainerUtil.newArrayList();
    categories = ContainerUtil.newStack();
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
      currentPlugin = new PluginNode();
      currentPlugin.setCategory(buildCategoryName());
      currentPlugin.setDownloads(attributes.getValue(DOWNLOADS));
      currentPlugin.setSize(attributes.getValue(SIZE));
      currentPlugin.setUrl(attributes.getValue(URL));
      String dateString = attributes.getValue(DATE);
      if (dateString != null) {
        currentPlugin.setDate(dateString);
      }
      currentPlugin.setIncomplete(false);
      plugins.add(currentPlugin);
    }
    else if (qName.equals(IDEA_VERSION)) {
      currentPlugin.setSinceBuild(attributes.getValue(SINCE_BUILD));
      currentPlugin.setUntilBuild(IdeaPluginDescriptorImpl.convertExplicitBigNumberInUntilBuildToStar(attributes.getValue(UNTIL_BUILD)));
    }
    else if (qName.equals(VENDOR)) {
      currentPlugin.setVendorEmail(attributes.getValue(EMAIL));
      currentPlugin.setVendorUrl(attributes.getValue(URL));
    }
    else if (qName.equals(PLUGIN)) {
      currentPlugin = new PluginNode();
      String id = attributes.getValue(ID);
      if (id != null) {
        currentPlugin.setId(id);
      }
      currentPlugin.setDownloadUrl(attributes.getValue(URL));
      currentPlugin.setVersion(attributes.getValue(VERSION));
      currentPlugin.setIncomplete(true);
      plugins.add(currentPlugin);
    }
    currentValue.setLength(0);
  }

  @Override
  public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
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
      currentPlugin.addDepends(currentValueString);
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
      currentPlugin = null;
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    currentValue.append(ch, start, length);
  }

  private String buildCategoryName() {
    if (categoryName == null) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < categories.size(); i++) {
        if (i > 0) builder.append('/');
        builder.append(categories.get(i));
      }
      categoryName = builder.toString();
    }
    return categoryName;
  }
}
