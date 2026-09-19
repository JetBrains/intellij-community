// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.PlatformUtils;
import com.intellij.util.xml.dom.XmlDomReader;
import com.intellij.util.xml.dom.XmlElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

public final class ApplicationNamesInfo {
  /**
   * The path of an application info file that replaces the {@code idea/<prefix>ApplicationInfo.xml} resource.
   * Only a product with the {@link PlatformUtils#GATEWAY_PREFIX} prefix reads it.
   * {@code GatewayStarter} is the only producer: it stamps the file when it starts Gateway from another IDE.
   * Every other product ignores the property, because the application info holds licensing inputs.
   */
  @ApiStatus.Internal
  public static final String APPLICATION_INFO_FILE_PROPERTY = "idea.application.info.value";

  private final String myProductName;
  private final String myFullProductName;
  private final String myEditionName;
  private final String myScriptName;
  private final String myMotto;

  private static volatile ApplicationNamesInfo instance;
  private static volatile XmlElement rawData;

  /**
   * Reads the application info by the current platform prefix on every call.
   * Production code must use {@link #initAndGetRawData()}, which caches the result.
   */
  @ApiStatus.Internal
  @VisibleForTesting
  public static @NotNull XmlElement loadData() {
    XmlElement data;
    String prefix = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "");
    String file = prefix.equals(PlatformUtils.GATEWAY_PREFIX) ? System.getProperty(APPLICATION_INFO_FILE_PROPERTY) : null;
    if (file != null) {
      try {
        data = XmlDomReader.readXmlAsModel(Files.newInputStream(Paths.get(file)));
      }
      catch (Exception e) {
        throw new RuntimeException("Cannot load custom application info file " + file, e);
      }
    }
    else {
      String resource = "idea/" + (prefix.equals(PlatformUtils.IDEA_PREFIX) ? "" : prefix) + "ApplicationInfo.xml";
      InputStream stream = ApplicationNamesInfo.class.getClassLoader().getResourceAsStream(resource);
      if (stream == null) {
        throw new RuntimeException("Resource not found: " + resource);
      }
      try {
        data = XmlDomReader.readXmlAsModel(stream);
      }
      catch (Exception e) {
        throw new RuntimeException("Cannot load resource: " + resource, e);
      }
    }
    if (PlatformUtils.isQodana()) {
      setQodanaProductAttributes(data);
    }
    return data;
  }

  private static void setQodanaProductAttributes(XmlElement data) {
    XmlElement namesNode = data.getChild("names");
    assert namesNode != null;
    String qodanaProductName = System.getProperty("qodana.product.name", "Qodana");
    namesNode.attributes.put("product", qodanaProductName);
    namesNode.attributes.put("fullname", qodanaProductName);

    XmlElement buildNode = data.getChild("build");
    assert buildNode != null;
    buildNode.attributes.put("number", System.getProperty("qodana.build.number", "QD-SNAPSHOT"));

    String qodanaEap = System.getProperty("qodana.eap", "false");
    XmlElement versionNode = data.getChild("version");
    assert versionNode != null;
    versionNode.attributes.put("eap", qodanaEap);
  }

  private static @NotNull XmlElement getRawData() {
    XmlElement result = rawData;
    if (result == null) {
      //noinspection SynchronizeOnThis
      synchronized (ApplicationNamesInfo.class) {
        result = rawData;
        if (result == null) {
          result = loadData();
          rawData = result;
        }
      }
    }
    return result;
  }

  /**
   * Returns the raw application info and initializes {@link #getInstance()} from it.
   * The first call resolves the resource by the platform prefix.
   * Every later call returns the same element, so a later prefix change has no effect.
   */
  @ApiStatus.Internal
  public static @NotNull XmlElement initAndGetRawData() {
    XmlElement data = getRawData();
    if (instance == null) {
      //noinspection SynchronizeOnThis
      synchronized (ApplicationNamesInfo.class) {
        if (instance == null) {
          instance = new ApplicationNamesInfo(data);
        }
      }
    }
    return data;
  }

  public static @NotNull ApplicationNamesInfo getInstance() {
    ApplicationNamesInfo result = instance;
    if (result == null) {
      //noinspection SynchronizeOnThis
      synchronized (ApplicationNamesInfo.class) {
        result = instance;
        if (result == null) {
          result = new ApplicationNamesInfo(getRawData());
          instance = result;
        }
      }
    }
    return result;
  }

  private ApplicationNamesInfo(XmlElement rootElement) {
    XmlElement names = rootElement.getChild("names");
    assert names != null;
    myProductName = names.getAttributeValue("product");
    myFullProductName = names.getAttributeValue("fullname", myProductName);
    String editionName = names.getAttributeValue("edition");
    myEditionName = editionName == null || editionName.isEmpty() ? null : editionName;
    myScriptName = names.getAttributeValue("script");
    myMotto = names.getAttributeValue("motto", "The Drive to Develop");
  }

  /**
   * For multi-word product names, returns a short variant (e.g. {@code "IDEA"} for "IntelliJ IDEA"),
   * otherwise returns the same value as {@link #getFullProductName()}.
   * <strong>Consider using {@link #getFullProductName()} instead.</strong>
   */
  public @NlsSafe String getProductName() {
    return myProductName;
  }

  /**
   * Returns full product name ({@code "IntelliJ IDEA"} for IntelliJ IDEA, {@code "WebStorm"} for WebStorm, etc.).
   * Vendor prefix and edition are not included.
   */
  public @NlsSafe String getFullProductName() {
    return myFullProductName;
  }

  /**
   * <p>Returns the full product name with the edition. Vendor prefix is not included.</p>
   *
   * <p>Use only when omitting an edition may potentially cause confusion.<br/>
   * Example #1: include the edition in generated shortcuts, since a user may have several editions installed.<br/>
   * Example #2: exclude the edition from "Restart ...?" confirmation, as it only hampers readability.</p>
   *
   * <p><strong>Rarely needed, consider using {@link #getFullProductName()} instead.</strong></p>
   *
   * @see #getFullProductName()
   * @see #getEditionName()
   */
  public @NlsSafe String getFullProductNameWithEdition() {
    return myEditionName == null ? myFullProductName : myFullProductName + ' ' + myEditionName;
  }

  /**
   * Returns edition name of the product, if applicable (e.g., {@code "Educational Edition"}).
   */
  public @NlsSafe @Nullable String getEditionName() {
    return myEditionName;
  }

  /**
   * Returns a sentence-cased version of {@link #getProductName()} ({@code "Idea"} for IntelliJ IDEA, {@code "Webstorm"} for WebStorm, etc.).
   * <strong>Kept for compatibility; use {@link #getFullProductName()} instead.</strong>
   */
  public String getLowercaseProductName() {
    String s = myProductName.toLowerCase(Locale.ENGLISH);
    return Character.isUpperCase(s.charAt(0)) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * Returns the base name (i.e., a name without the extension and architecture suffix)
   * of launcher files (bin/xxx64.exe, bin/xxx.bat, bin/xxx.sh, macOS/xxx)
   * ({@code "idea"} for IntelliJ IDEA, {@code "webstorm"} for WebStorm, etc.).
   */
  public String getScriptName() {
    return myScriptName;
  }

  /**
   * Returns the motto of the product. Used as a comment for a desktop entry on XDG-compliant systems (read "Linux").
   */
  public @NotNull String getMotto() {
    return myMotto;
  }
}
