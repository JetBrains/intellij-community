// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.idea.AppMode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.PlatformUtils;
import com.intellij.util.xml.dom.XmlDomReader;
import com.intellij.util.xml.dom.XmlElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class ApplicationNamesInfo {
  private final String myProductName;
  private final String myFullProductName;
  private final String myEditionName;
  private final String myScriptName;
  private final String myMotto;

  private static volatile ApplicationNamesInfo instance;

  private static @NotNull XmlElement loadData() {
    String prefix = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "");
    String appInfoData = getAppInfoData();

    if (AppMode.isDevServer() && appInfoData.isEmpty()) {
      String module = null;
      if (prefix.isEmpty() || prefix.equals(PlatformUtils.IDEA_PREFIX)) {
        module = "intellij.idea.ultimate.customization";
      }
      else if (prefix.equals(PlatformUtils.WEB_PREFIX)) {
        module = "intellij.webstorm";
      }

      if (module != null) {
        String resource = (prefix.equals("idea") ? "" : prefix) + "ApplicationInfo.xml";
        Path file = PathManager.getHomeDir().resolve("out/classes/production/" + module + "/idea/" + resource);
        try {
          return XmlDomReader.readXmlAsModel(Files.newInputStream(file));
        }
        catch (NoSuchFileException ignore) { }
        catch (Exception e) {
          throw new RuntimeException("Cannot load " + file, e);
        }
      }
    }
    else {
      // Gateway started from another IntelliJ-based IDE; same for Qodana
      if (prefix.equals(PlatformUtils.GATEWAY_PREFIX)) {
        String customAppInfo = System.getProperty("idea.application.info.value");
        if (customAppInfo != null) {
          try {
            Path file = Paths.get(customAppInfo);
            return XmlDomReader.readXmlAsModel(Files.newInputStream(file));
          }
          catch (Exception e) {
            throw new RuntimeException("Cannot load custom application info file " + customAppInfo, e);
          }
        }
      }

      // this property is used when a product is started from distribution of another product
      boolean forceLoadingFromResources = "true".equals(System.getProperty("intellij.platform.load.app.info.from.resources"));
      if (!forceLoadingFromResources && !appInfoData.isEmpty()) {
        return XmlDomReader.readXmlAsModel(appInfoData.getBytes(StandardCharsets.UTF_8));
      }
    }

    // from sources or from another product
    String resource = "idea/" + (prefix.equals("idea") ? "" : prefix) + "ApplicationInfo.xml";
    InputStream stream = ApplicationNamesInfo.class.getClassLoader().getResourceAsStream(resource);
    if (stream == null) {
      throw new RuntimeException("Resource not found: " + resource);
    }

    try {
      XmlElement data = XmlDomReader.readXmlAsModel(stream);
      if (PlatformUtils.isQodana()) {
        setQodanaProductAttributes(data);
      }
      return data;
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot load resource: " + resource, e);
    }
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

  @ApiStatus.Internal
  public static String getAppInfoData() {
    // not easy to inject a byte array using ASM - it is not constant value
    return "";
  }

  @ApiStatus.Internal
  public static @NotNull XmlElement initAndGetRawData() {
    //noinspection SynchronizeOnThis
    synchronized (ApplicationNamesInfo.class) {
      XmlElement data = loadData();
      if (instance == null) {
        instance = new ApplicationNamesInfo(data);
      }
      return data;
    }
  }

  public static @NotNull ApplicationNamesInfo getInstance() {
    ApplicationNamesInfo result = instance;
    if (result == null) {
      //noinspection SynchronizeOnThis
      synchronized (ApplicationNamesInfo.class) {
        result = instance;
        if (result == null) {
          result = new ApplicationNamesInfo(loadData());
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
    myEditionName = names.getAttributeValue("edition");
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
   * <p>Returns full product name with edition. Vendor prefix is not included.</p>
   *
   * <p>Use only when omitting an edition may potentially cause a confusion.<br/>
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
   * Returns edition name of the product, if applicable
   * (e.g. {@code "Ultimate Edition"} or {@code "Community Edition"} for IntelliJ IDEA, {@code null} for WebStorm).
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
   * Returns motto of the product. Used as a comment for a desktop entry on XDG-compliant systems (read "Linux").
   */
  public @NotNull String getMotto() {
    return myMotto;
  }
}
