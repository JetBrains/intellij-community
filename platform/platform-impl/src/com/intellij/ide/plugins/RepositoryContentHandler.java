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

import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

public class RepositoryContentHandler extends DefaultHandler {
  @NonNls public static final String CATEGORY = "category";
  @NonNls public static final String IDEA_PLUGIN = "idea-plugin";
  @NonNls public static final String NAME = "name";
  @NonNls public static final String ID = "id";
  @NonNls public static final String DESCRIPTION = "description";
  @NonNls public static final String VERSION = "version";
  @NonNls public static final String VENDOR = "vendor";
  @NonNls public static final String EMAIL = "email";
  @NonNls public static final String URL = "url";
  @NonNls public static final String IDEA_VERSION = "idea-version";
  @NonNls public static final String SINCE_BUILD = "since-build";
  @NonNls private static final String UNTIL_BUILD = "until-build";
  @NonNls public static final String CHNAGE_NOTES = "change-notes";
  @NonNls private static final String DEPENDS = "depends";
  @NonNls private static final String DOWNLOADS = "downloads";
  @NonNls private static final String DOWNLOAD_URL = "downloadUrl";
  @NonNls private static final String DOWNLOAD_URL_NEW_STYLE = "download-url";
  @NonNls private static final String SIZE = "size";

  @NonNls private static final String RATING = "rating";
  @NonNls private static final String DATE = "date";
  private PluginNode currentPlugin;
  private final StringBuilder currentValue = new StringBuilder();
  private ArrayList<IdeaPluginDescriptor> plugins;
  private Stack<String> categoriesStack;


  @Override
  public void startDocument() throws SAXException {
    plugins = new ArrayList<IdeaPluginDescriptor>();
    categoriesStack = new Stack<String>();
  }

  @Override
  public void startElement(@NotNull String namespaceURI, @NotNull String localName, @NotNull String qName, @NotNull Attributes attributes) throws SAXException {
    if (qName.equals(CATEGORY)) {
      categoriesStack.push(attributes.getValue(NAME));
    }
    else if (qName.equals(IDEA_PLUGIN)) {
      String categoryName = constructCategoryTree();
      currentPlugin = new PluginNode();
      currentPlugin.setCategory(categoryName);
      currentPlugin.setDownloads(attributes.getValue(DOWNLOADS));
      currentPlugin.setSize(attributes.getValue(SIZE));
      currentPlugin.setUrl(attributes.getValue(URL));
      final String dateString = attributes.getValue(DATE);
      if (dateString != null) {
        currentPlugin.setDate(dateString);
      }

      plugins.add(currentPlugin);
    }
    else if (qName.equals(IDEA_VERSION)) {
      currentPlugin.setSinceBuild(attributes.getValue(SINCE_BUILD));
      currentPlugin.setUntilBuild(attributes.getValue(UNTIL_BUILD));
    }
    else if (qName.equals(VENDOR)) {
      currentPlugin.setVendorEmail(attributes.getValue(EMAIL));
      currentPlugin.setVendorUrl(attributes.getValue(URL));
    }
    currentValue.setLength(0);
  }

  @Override
  public void endElement(String namespaceURI, @NotNull String localName, @NotNull String qName) throws SAXException {
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
      currentPlugin.addDepends(PluginId.getId(currentValueString));
    }
    else if (qName.equals(CHNAGE_NOTES)) {
      currentPlugin.setChangeNotes(currentValueString);
    }
    else if (qName.equals(CATEGORY)) {
      categoriesStack.pop();
    }
    else if (qName.equals(RATING)) {
      currentPlugin.setRating(currentValueString);
    }
    else if (qName.equals(DOWNLOAD_URL) || qName.equals(DOWNLOAD_URL_NEW_STYLE)) {
      currentPlugin.setDownloadUrl(currentValueString);
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    currentValue.append(ch, start, length);
  }

  public List<IdeaPluginDescriptor> getPluginsList() {
    return plugins;
  }

  private String constructCategoryTree() {
    StringBuilder category = new StringBuilder();
    for (int i = 0; i < categoriesStack.size(); i++) {
      String str = categoriesStack.get(i);
      if (str.length() > 0) {
        if (i > 0) category.append("/");
        category.append(str);
      }
    }
    return category.toString();
  }
}
