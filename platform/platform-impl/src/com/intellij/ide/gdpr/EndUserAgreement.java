// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.ide.Prefs;
import com.intellij.l10n.LocalizationUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ResourceUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class EndUserAgreement {
  private static final Logger LOG = Logger.getInstance(EndUserAgreement.class);
  private static final String POLICY_TEXT_PROPERTY = "jb.privacy.policy.text"; // to be used in tests to pass arbitrary policy text

  private static final String PRIVACY_POLICY_DOCUMENT_NAME = "privacy";
  private static final String PRIVACY_POLICY_EAP_DOCUMENT_NAME = PRIVACY_POLICY_DOCUMENT_NAME + "Eap";
  private static final String CWM_GUEST_EULA_NAME = "cwmGuestEua";
  private static final String EULA_DOCUMENT_NAME = "eua";
  private static final String EULA_COMMUNITY_DOCUMENT_NAME = "euaCommunity";
  private static final String EULA_EAP_DOCUMENT_NAME = EULA_DOCUMENT_NAME + "Eap";

  private static final String PRIVACY_POLICY_CONTENT_FILE_NAME = "Cached";

  private static final String DEFAULT_DOC_NAME = EULA_DOCUMENT_NAME;
  private static final String DEFAULT_DOC_EAP_NAME = EULA_EAP_DOCUMENT_NAME;

  private static final String ACTIVE_DOC_FILE_NAME = "documentName";
  private static final String ACTIVE_DOC_EAP_FILE_NAME = "documentName.eap";

  private static final String RELATIVE_RESOURCE_PATH = "PrivacyPolicy";

  private static final String VERSION_COMMENT_START = "<!--";
  private static final String VERSION_COMMENT_END = "-->";
  private static final Version MAGIC_VERSION = new Version(999, 999);

  public static Path getDocumentContentFile() {
    return getDocumentContentFile(getDocumentName());
  }

  private static @NotNull Path getDocumentContentFile(@NotNull String docName) {
    return getDataRoot().resolve(PRIVACY_POLICY_DOCUMENT_NAME.equals(docName) ? PRIVACY_POLICY_CONTENT_FILE_NAME : (docName + ".cached"));
  }

  private static @NotNull Path getDocumentNameFile() {
    return getDataRoot().resolve(shouldUseEAPAgreement()? ACTIVE_DOC_EAP_FILE_NAME : ACTIVE_DOC_FILE_NAME);
  }

  private static boolean shouldUseEAPAgreement() {
    return ApplicationInfoImpl.getShadowInstance().isEAP() && !Agreements.isReleaseAgreementsEnabled();
  }

  private static @NotNull Path getDataRoot() {
    return PathManager.getCommonDataPath().resolve(RELATIVE_RESOURCE_PATH);
  }

  // path for classloader - without leading slash
  private static String getBundledResourcePath(String docName) {
    return PRIVACY_POLICY_DOCUMENT_NAME.equals(docName) ? "PrivacyPolicy.html" : docName + ".html";
  }

  public static void setAccepted(@NotNull Document doc) {
    final Version version = doc.getVersion();
    String versionKey = getAcceptedVersionKey(doc.getName());
    if (version.isUnknown()) {
      Prefs.remove(versionKey);
    }
    else {
      Prefs.put(versionKey, version.toString());
    }
  }

  public static @NotNull Version getAcceptedVersion(@NotNull String docName) {
    String versionKey = getAcceptedVersionKey(docName);
    return Version.fromString(Prefs.get(versionKey, null));
  }

  public static @NotNull Document getLatestDocument() {
    // needed for testing
    String text = System.getProperty(POLICY_TEXT_PROPERTY, null);
    if (text != null) {
      Document fromProperty = new Document(PRIVACY_POLICY_DOCUMENT_NAME, text);
      if (!fromProperty.getVersion().isUnknown()) {
        return fromProperty;
      }
    }

    String docName = getDocumentName();
    Document defaultDocument = loadDocument(docName);
    Locale locale = Locale.getDefault();
    List<String> localizedDocsNames = LocalizationUtil.INSTANCE.getSuffixLocalizedPaths(docName, locale);

    Document document;
    for (String localizedDocName : localizedDocsNames) {
      document = loadDocument(localizedDocName);
      if (!document.getText().isEmpty() && !defaultDocument.getVersion().isNewer(document.getVersion())) return document;
    }
    return defaultDocument;
  }

  private static @NotNull Document loadDocument(String docName) {
    Document fromFile = loadContent(docName, getDocumentContentFile(docName));
    if (fromFile != null && !fromFile.getVersion().isUnknown()) {
      return fromFile;
    }
    return loadContent(docName, getBundledResourcePath(docName));
  }

  public static void updateCachedContentToLatestBundledVersion() {
    String docName = getDocumentName();
    Locale locale = Locale.getDefault();
    List<String> localizedDocsNames = LocalizationUtil.INSTANCE.getSuffixLocalizedPaths(docName, locale);
    for (String localizedDocName : localizedDocsNames) {
      updateCachedContentToLatestBundledVersion(localizedDocName);
    }
    updateCachedContentToLatestBundledVersion(docName);
  }

  private static void updateCachedContentToLatestBundledVersion(@NotNull String docName) {
    try {
      Document cached = loadContent(docName, getDocumentContentFile(docName));
      if (cached == null || cached.getVersion().isUnknown()) {
        return;
      }

      Document bundled = loadContent(docName, getBundledResourcePath(docName));
      if (!bundled.getVersion().isUnknown() && bundled.getVersion().isNewer(cached.getVersion())) {
        // update content only and not the active document name
        // active document name can be changed by JBA only
        writeToFile(getDocumentContentFile(docName), bundled.getText());
      }
    }
    catch (Throwable ignored) { }
  }

  private static void writeToFile(@NotNull Path file, @NotNull String text) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, text);
    }
    catch (NoSuchFileException e) {
      LOG.info(e.getMessage());
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  public static void updateContent(@NotNull String docName, @NotNull String text) {
    writeToFile(getDocumentContentFile(docName), text);
  }

  public static void updateActiveDocumentName(@NotNull String docName) {
    writeToFile(getDocumentNameFile(), docName);
  }

  private static @NotNull Document loadContent(String docName, String resourcePath) {
    try {
      byte[] data = ResourceUtil.getResourceAsBytes(resourcePath, EndUserAgreement.class.getClassLoader());
      if (data != null) {
        return new Document(docName, new String(data, StandardCharsets.UTF_8));
      }
    }
    catch (IOException e) {
      LOG.info(docName + ": " + e.getMessage());
      LOG.debug(e);
    }
    return new Document(docName, "");
  }

  public static @Nullable Document loadContent(String docName, Path file) {
    try {
      return new Document(docName, Files.readString(file));
    }
    catch (NoSuchFileException ignored) {
      return null;
    }
    catch (IOException e) {
      LOG.info(docName + ": " + e.getMessage());
      LOG.debug(e);
    }
    return new Document(docName, "");
  }

  private static @NotNull String getDocumentName() {
    if (!PlatformUtils.isCommercialEdition()) {
      if (PlatformUtils.isCommunityEdition()) {
        return shouldUseEAPAgreement()? DEFAULT_DOC_EAP_NAME : EULA_COMMUNITY_DOCUMENT_NAME;
      }
      if (PlatformUtils.isJetBrainsClient()) {
        return CWM_GUEST_EULA_NAME;
      }
      if (PlatformUtils.isGateway()) {
        return shouldUseEAPAgreement()? DEFAULT_DOC_EAP_NAME : DEFAULT_DOC_NAME;
      }
      return shouldUseEAPAgreement()? PRIVACY_POLICY_EAP_DOCUMENT_NAME : PRIVACY_POLICY_DOCUMENT_NAME;
    }

    try {
      String docName = Files.readString(getDocumentNameFile());
      if (isValidFileName(docName)) {
        return docName;
      }
    }
    catch (IOException ignored) {
    }
    return shouldUseEAPAgreement()? DEFAULT_DOC_EAP_NAME : DEFAULT_DOC_NAME;
  }

  private static boolean isValidFileName(String docName) {
    if (docName != null && !docName.isBlank()) {
      try {
        Paths.get(docName);
        return true;
      }
      catch (InvalidPathException ignored) {
      }
    }
    return false;
  }

  private static @NotNull String getAcceptedVersionKey(@NotNull String docName) {
    if (PRIVACY_POLICY_DOCUMENT_NAME.equals(docName)) {
      return "JetBrains.privacy_policy.accepted_version";
    }
    String keyName = docName;
    if (EULA_EAP_DOCUMENT_NAME.equals(docName)) {
      // for commercial EAP releases accepted version attribute should be separate from the one Resharper uses (IDEA-212020)
      keyName = "ij_" + keyName;
    }
    return "JetBrains.privacy_policy." + keyName + "_accepted_version";
  }

  public static final class Document {
    private final String myName;
    private final String myText;
    private final Version myVersion;

    public Document(String name, String text) {
      myName = name;
      myText = text;
      myVersion = parseVersion(text);
    }

    public boolean isPrivacyPolicy() {
      return PRIVACY_POLICY_DOCUMENT_NAME.equals(myName) || PRIVACY_POLICY_EAP_DOCUMENT_NAME.equals(myName);
    }

    public boolean isAccepted() {
      final Version thisVersion = getVersion();
      if (thisVersion.isUnknown() || MAGIC_VERSION.equals(thisVersion)) {
        return true;
      }
      final Version acceptedByUser = getAcceptedVersion(getName());
      return !acceptedByUser.isUnknown() && acceptedByUser.getMajor() >= thisVersion.getMajor();
    }

    public String getName() {
      return myName;
    }

    public Version getVersion() {
      return myVersion;
    }

    public String getText() {
      return myText;
    }

    private static @NotNull Version parseVersion(String text) {
      if (text == null || text.isBlank()) {
        return Version.UNKNOWN;
      }

      Iterator<String> iterator = text.lines().iterator();
      while (iterator.hasNext()) {
        String line = iterator.next();
        int startComment = line.indexOf(VERSION_COMMENT_START);
        if (startComment >= 0) {
          int endComment = line.indexOf(VERSION_COMMENT_END);
          if (endComment > startComment) {
            return Version.fromString(line.substring(startComment + VERSION_COMMENT_START.length(), endComment).trim());
          }
        }
      }
      return Version.UNKNOWN;
    }
  }

  public static final class PluginAgreementUpdateDescriptor {
    private static final ExtensionPointName<PluginAgreementUpdateDescriptor> EP_NAME = ExtensionPointName.create("com.intellij.endUserAgreementUpdater");

    @Attribute("productCode")
    public String productCode;
    @Attribute("documentName")
    public String documentName;

    public static @NotNull List<PluginAgreementUpdateDescriptor> getDescriptors() {
      return EP_NAME.getExtensionList();
    }
  }
}
