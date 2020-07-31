// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr;

import com.intellij.ide.Prefs;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Eugene Zhuravlev
 *         Date: 09-Mar-16
 */
public final class EndUserAgreement {
  private static final Logger LOG = Logger.getInstance(EndUserAgreement.class);
  private static final String POLICY_TEXT_PROPERTY = "jb.privacy.policy.text"; // to be used in tests to pass arbitrary policy text

  private static final String PRIVACY_POLICY_DOCUMENT_NAME = "privacy";
  private static final String PRIVACY_POLICY_EAP_DOCUMENT_NAME = PRIVACY_POLICY_DOCUMENT_NAME + "Eap";
  private static final String EULA_DOCUMENT_NAME = "eua";
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

  private static @NotNull Path getDocumentContentFile(String docName) {
    return getDataRoot().resolve(PRIVACY_POLICY_DOCUMENT_NAME.equals(docName)? PRIVACY_POLICY_CONTENT_FILE_NAME : docName + ".cached");
  }

  private static @NotNull Path getDocumentNameFile() {
    return getDataRoot().resolve(isEAP() ? ACTIVE_DOC_EAP_FILE_NAME : ACTIVE_DOC_FILE_NAME);
  }

  private static boolean isEAP() {
    return ApplicationInfoImpl.getShadowInstance().isEAP();
  }

  private static @NotNull Path getDataRoot() {
    return PathManager.getCommonDataPath().resolve(RELATIVE_RESOURCE_PATH);
  }

  private static String getBundledResourcePath(String docName) {
    return PRIVACY_POLICY_DOCUMENT_NAME.equals(docName) ? "/PrivacyPolicy.html" : "/" + docName + ".html";
  }

  public static void setAccepted(@NotNull Document doc) {
    final Version version = doc.getVersion();
    if (version.isUnknown()) {
      Prefs.remove(getAcceptedVersionKey(doc.getName()));
    }
    else {
      Prefs.put(getAcceptedVersionKey(doc.getName()), version.toString());
    }
  }

  private static @NotNull Version getAcceptedVersion(String docName) {
    return Version.fromString(Prefs.get(getAcceptedVersionKey(docName), null));
  }

  public static @NotNull Document getLatestDocument() {
    // needed for testing
    final String text = System.getProperty(POLICY_TEXT_PROPERTY, null);
    if (text != null) {
      final Document fromProperty = loadContent(PRIVACY_POLICY_DOCUMENT_NAME, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
      if (!fromProperty.getVersion().isUnknown()) {
        return fromProperty;
      }
    }

    String docName = getDocumentName();
    try {
      Document fromFile = loadContent(docName, Files.newInputStream(getDocumentContentFile(docName)));
      if (!fromFile.getVersion().isUnknown()) {
        return fromFile;
      }
    }
    catch (IOException ignored) {
    }
    return loadContent(docName, EndUserAgreement.class.getResourceAsStream(getBundledResourcePath(docName)));
  }

  public static boolean updateCachedContentToLatestBundledVersion() {
    try {
      final String docName = getDocumentName();
      Path cacheFile = getDocumentContentFile(docName);
      if (Files.exists(cacheFile)) {
        Document cached = loadContent(docName, Files.newInputStream(cacheFile));
        if (!cached.getVersion().isUnknown()) {
          final Document bundled = loadContent(docName, EndUserAgreement.class.getResourceAsStream(getBundledResourcePath(docName)));
          if (!bundled.getVersion().isUnknown() && bundled.getVersion().isNewer(cached.getVersion())) {
            try {
              // update content only and not the active document name
              // active document name can be changed by JBA only
              FileUtil.writeToFile(getDocumentContentFile(docName).toFile(), bundled.getText());
            }
            catch (FileNotFoundException e) {
              LOG.info(e.getMessage());
            }
            catch (IOException e) {
              LOG.info(e);
            }
            return true;
          }
        }
      }
    }
    catch (Throwable ignored) {
    }
    return false;
  }

  public static void update(String docName, String text) {
    try {
      FileUtil.writeToFile(getDocumentContentFile(docName).toFile(), text);
      FileUtil.writeToFile(getDocumentNameFile().toFile(), docName);
    }
    catch (FileNotFoundException e) {
      LOG.info(e.getMessage());
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  private static @NotNull Document loadContent(String docName, InputStream stream) {
    if (stream != null) {
      try (Reader reader = new InputStreamReader(stream instanceof ByteArrayInputStream ? stream : new BufferedInputStream(stream),
                                                 StandardCharsets.UTF_8)) {
        return new Document(docName, new String(FileUtil.adaptiveLoadText(reader)));
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return new Document(docName, "");
  }

  private static @NotNull String getDocumentName() {
    if (!PlatformUtils.isCommercialEdition()) {
      return isEAP()? PRIVACY_POLICY_EAP_DOCUMENT_NAME : PRIVACY_POLICY_DOCUMENT_NAME;
    }

    try {
      String docName = new String(Files.readAllBytes(getDocumentNameFile()), StandardCharsets.UTF_8);
      if (!StringUtilRt.isEmptyOrSpaces(docName)) {
        return docName;
      }
    }
    catch (IOException ignored) {
    }
    return isEAP()? DEFAULT_DOC_EAP_NAME : DEFAULT_DOC_NAME;
  }

  private static @NotNull String getAcceptedVersionKey(String docName) {
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
      if (!StringUtil.isEmptyOrSpaces(text)) {
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
          final String line = reader.readLine();
          if (line != null) {
            final int startComment = line.indexOf(VERSION_COMMENT_START);
            if (startComment >= 0) {
              final int endComment = line.indexOf(VERSION_COMMENT_END);
              if (endComment > startComment) {
                return Version.fromString(line.substring(startComment + VERSION_COMMENT_START.length(), endComment).trim());
              }
            }
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
      return Version.UNKNOWN;
    }

  }
}