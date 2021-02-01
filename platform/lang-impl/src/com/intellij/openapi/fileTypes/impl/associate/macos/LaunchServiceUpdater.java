// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate.macos;

import com.intellij.openapi.fileTypes.impl.associate.OSAssociateFileTypesUtil;
import com.intellij.openapi.fileTypes.impl.associate.OSFileAssociationException;
import com.intellij.openapi.fileTypes.impl.associate.macos.PListBuddyWrapper.CommandResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * Based on idea from:
 * <a href="https://superuser.com/questions/273756/how-to-change-default-app-for-all-files-of-particular-file-type-through-terminal">
 *   How to change default app for all files of particular file type through terminal in OS X?
 * </a>
 */
final class LaunchServiceUpdater {
  public final static String UPDATE_FAILURE_MSG = "Launch services PList updated failed, the error is logged.";

  private final static Logger LOG = Logger.getInstance(LaunchServiceUpdater.class);

  private final String myBundleId;
  private final Set<String> myUriSet = new HashSet<>();
  private final Set<String> myExtensionSet = new HashSet<>();

  LaunchServiceUpdater(String id) {
    myBundleId = id;
  }

  void addFileTypes(@NotNull List<? extends FileType> fileTypes) throws OSFileAssociationException {
    for (FileType fileType : fileTypes) {
      String[] uri = UniformIdentifierUtil.getURIs(fileType);
      if (uri.length > 0) {
        Collections.addAll(myUriSet, UniformIdentifierUtil.getURIs(fileType));
      }
      else {
        myExtensionSet.addAll(OSAssociateFileTypesUtil.getExtensions(fileType));
      }
    }
  }

  void update() throws OSFileAssociationException {
    removeExistingEntries();
    for (String uri : myUriSet) {
      createContentTypeEntry(uri);
    }
    for (String ext : myExtensionSet) {
      createExtensionEntry(ext);
    }
  }

  private void removeExistingEntries() throws OSFileAssociationException {
    PListBuddyWrapper buddy = new PListBuddyWrapper();
    Document handlers = buddy.readData("LSHandlers");
    Node arrayNode = getTopArrayNode(handlers);
    List<Integer> entriesToRemove = new ArrayList<>();
    if (arrayNode != null) {
      Node[] entries = getEntries(arrayNode);
      for (int i = 0; i < entries.length; i ++) {
        String uri = extractUri(entries[i]);
        if (myUriSet.contains(uri)) {
          entriesToRemove.add(i);
        }
      }
    }
    if (!entriesToRemove.isEmpty()) {
      Collections.reverse(entriesToRemove); // Start from the last
      for (Integer index : entriesToRemove) {
        CommandResult result = buddy.runCommand("Delete LSHandlers:" + index);
        if (result.retCode != 0) {
          LOG.warn("PListBuddy returned: " + result.retCode + " for index " + index);
          throw new OSFileAssociationException(UPDATE_FAILURE_MSG);
        }
      }
    }
  }

  private static Node[] getEntries(@NotNull Node arrayNode) {
    List<Node> entryList = new ArrayList<>();
    NodeList children = arrayNode.getChildNodes();
    for (int i = 0; i < children.getLength(); i ++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        entryList.add(child);
      }
    }
    return entryList.toArray(new Node[0]);
  }

  @Nullable
  private static Node getTopArrayNode(@NotNull Document document) {
    NodeList arrayNodes = document.getElementsByTagName("array");
    for (int i = 0; i < arrayNodes.getLength(); i ++) {
      Node child = arrayNodes.item(i);
      Node parent = child.getParentNode();
      if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE && "plist".equals(parent.getNodeName())) {
        return child;
      }
    }
    return null;
  }

  @Nullable
  private static String extractUri(@NotNull Node dictNode) {
    NodeList children = dictNode.getChildNodes();
    for (int i = 0; i < children.getLength(); i ++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE &&
          "key".equals(child.getNodeName()) &&
          "LSHandlerContentType".equals(child.getTextContent())) {
        Node valueNode = child.getNextSibling();
        if (valueNode != null && valueNode.getNodeType() == Node.TEXT_NODE) {
          valueNode = valueNode.getNextSibling();
        }
        if (valueNode != null) {
          assert valueNode.getNodeType() == Node.ELEMENT_NODE;
          return valueNode.getTextContent();
        }
      }
    }
    return null;
  }

  private void createContentTypeEntry(@NotNull String uri) throws OSFileAssociationException {
    PListBuddyWrapper buddy = new PListBuddyWrapper();
    buddy.runCommand(PListBuddyWrapper.OutputType.DEFAULT,
                     "Add LSHandlers:0 dict",
                     "Add LSHandlers:0:LSHandlerContentType string " + uri,
                     "Add LSHandlers:0:LSHandlerRoleAll string " + myBundleId);
  }

  private void createExtensionEntry(@NotNull String extension) throws OSFileAssociationException {
    PListBuddyWrapper buddy = new PListBuddyWrapper();
    buddy.runCommand(PListBuddyWrapper.OutputType.DEFAULT,
                     "Add LSHandlers:0 dict",
                     "Add LSHandlers:0:LSHandlerContentTag string " + extension,
                     "Add LSHandlers:0:LSHandlerContentTagClass string public.filename-extension",
                     "Add LSHandlers:0:LSHandlerRoleAll string " + myBundleId);
  }
}
