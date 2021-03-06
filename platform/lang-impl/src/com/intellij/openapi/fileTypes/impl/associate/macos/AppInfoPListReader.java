// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate.macos;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.impl.associate.OSFileAssociationException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;

class AppInfoPListReader {
  private final static String BUNDLE_IDENTIFIER = "CFBundleIdentifier";

  private final static String DEBUG_HOME_PATH = System.getProperty("file.association.debug.home.path");

  private final static Logger LOG = Logger.getInstance(AppInfoPListReader.class);
  private Document myDocument;

  @Nullable
  public String getBundleIdentifier() {
    NodeList dictList = getDocument().getElementsByTagName("dict");
    for (int i = 0; i < dictList.getLength(); i ++) {
      Node dictNode = dictList.item(i);
      NodeList dataList = dictNode.getChildNodes();
      for (int j = 0; j < dataList.getLength(); j ++) {
        Node dataNode = dataList.item(j);
        if (BUNDLE_IDENTIFIER.equals(dataNode.getTextContent())) {
          Node nextNode = dataNode.getNextSibling();
          while (nextNode != null) {
            if (nextNode.getNodeType() == Node.ELEMENT_NODE) {
              break;
            }
            nextNode = nextNode.getNextSibling();
          }
          String value = nextNode != null ? StringUtil.trim(nextNode.getTextContent()) : null;
          if (!StringUtil.isEmpty(value)) return value;
        }
      }
    }
    return null;
  }

  @NotNull
  private Document getDocument() {
    if (myDocument == null) {
      LOG.error("Info.plist not loaded");
    }
    return myDocument;
  }

  @NotNull
  private static File getInfoPListFile() throws OSFileAssociationException {
    String homePath = ObjectUtils.notNull(DEBUG_HOME_PATH, PathManager.getHomePath());
    File infoFile = new File(homePath + File.separator + "Info.plist");
    if (!infoFile.exists()) {
      throw new OSFileAssociationException("Info.plist was not found at " + homePath + ", please try to reinstall the application");
    }
    return infoFile;
  }

  void loadPList() throws OSFileAssociationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      myDocument = builder.parse(getInfoPListFile());
    }
    catch (ParserConfigurationException e) {
      LOG.error(e);
    }
    catch (SAXException | IOException e) {
      throw new OSFileAssociationException("Error reading Info.plist: " + e.getMessage());
    }
  }
}
