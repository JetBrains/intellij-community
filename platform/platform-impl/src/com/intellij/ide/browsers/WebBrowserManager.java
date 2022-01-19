// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Service(Service.Level.APP)
@State(name = "WebBrowsersConfiguration", storages = @Storage(value = "web-browsers.xml", roamingType = RoamingType.DISABLED))
public final class WebBrowserManager extends SimpleModificationTracker implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(WebBrowserManager.class);

  // default standard browser ID must be constant across all IDE versions on all machines for all users
  private static final UUID PREDEFINED_CHROME_ID = UUID.fromString("98CA6316-2F89-46D9-A9E5-FA9E2B0625B3");
  @ApiStatus.Internal
  public static final UUID PREDEFINED_FIREFOX_ID = UUID.fromString("A7BB68E0-33C0-4D6F-A81A-AAC1FDB870C8");
  private static final UUID PREDEFINED_SAFARI_ID = UUID.fromString("E5120D43-2C3F-47EF-9F26-65E539E05186");
  private static final UUID PREDEFINED_OPERA_ID = UUID.fromString("53E2F627-B1A7-4DFA-BFA7-5B83CC034776");
  private static final UUID PREDEFINED_YANDEX_ID = UUID.fromString("B1B2EC2C-20BD-4EE2-89C4-616DB004BCD4");
  private static final UUID PREDEFINED_EXPLORER_ID = UUID.fromString("16BF23D4-93E0-4FFC-BFD6-CB13575177B0");
  private static final UUID PREDEFINED_OLD_EDGE_ID = UUID.fromString("B2A9DCA7-9D0B-4E1E-98A8-AFB19C1328D2");
  private static final UUID PREDEFINED_EDGE_ID = UUID.fromString("37cae5b9-e8b2-4949-9172-aafa37fbc09c");

  private static final UUID[] PREDEFINED_BROWSER_IDS = new UUID[]{
    PREDEFINED_CHROME_ID,
    PREDEFINED_FIREFOX_ID,
    PREDEFINED_SAFARI_ID,
    PREDEFINED_OPERA_ID,
    PREDEFINED_YANDEX_ID,
    PREDEFINED_EXPLORER_ID,
    PREDEFINED_EDGE_ID
  };

  public static final ReloadMode BROWSER_RELOAD_MODE_DEFAULT = ReloadMode.RELOAD_ON_SAVE;
  public static final ReloadMode PREVIEW_RELOAD_MODE_DEFAULT = ReloadMode.RELOAD_ON_SAVE;

  private static @NotNull String getEdgeExecutionPath() {
    if (SystemInfo.isWindows) {
      return "msedge";
    }
    else if (SystemInfo.isMac) {
      return "Microsoft Edge";
    }
    else {
      return "microsoft-edge";
    }
  }

  private static List<ConfigurableWebBrowser> getPredefinedBrowsers() {
    return Arrays.asList(
      new ConfigurableWebBrowser(PREDEFINED_CHROME_ID, BrowserFamily.CHROME),
      new ConfigurableWebBrowser(PREDEFINED_FIREFOX_ID, BrowserFamily.FIREFOX),
      new ConfigurableWebBrowser(PREDEFINED_SAFARI_ID, BrowserFamily.SAFARI, BrowserFamily.SAFARI.getName(),
                                 BrowserFamily.SAFARI.getExecutionPath(), SystemInfo.isMac,
                                 BrowserFamily.SAFARI.createBrowserSpecificSettings()),
      new ConfigurableWebBrowser(PREDEFINED_OPERA_ID, BrowserFamily.CHROME, "Opera", SystemInfo.isMac ? "Opera" : "opera", false, null),
      new ConfigurableWebBrowser(PREDEFINED_YANDEX_ID, BrowserFamily.CHROME, "Yandex",
                                 SystemInfo.isWindows ? "browser" : (SystemInfo.isMac ? "Yandex" : "yandex"), false,
                                 BrowserFamily.CHROME.createBrowserSpecificSettings()),
      new ConfigurableWebBrowser(PREDEFINED_EXPLORER_ID, BrowserFamily.EXPLORER, BrowserFamily.EXPLORER.getName(),
                                 BrowserFamily.EXPLORER.getExecutionPath(), false,
                                 BrowserFamily.EXPLORER.createBrowserSpecificSettings()),
      new ConfigurableWebBrowser(PREDEFINED_EDGE_ID, BrowserFamily.CHROME, "Edge",
                                 getEdgeExecutionPath(), SystemInfo.isWindows,
                                 BrowserFamily.CHROME.createBrowserSpecificSettings())
    );
  }

  private List<ConfigurableWebBrowser> browsers;
  private boolean showBrowserHover = true;
  private boolean showBrowserHoverXml = false;
  DefaultBrowserPolicy defaultBrowserPolicy = DefaultBrowserPolicy.SYSTEM;
  ReloadMode webServerReloadMode = BROWSER_RELOAD_MODE_DEFAULT;
  ReloadMode webPreviewReloadMode = PREVIEW_RELOAD_MODE_DEFAULT;

  public WebBrowserManager() {
    browsers = new ArrayList<>(getPredefinedBrowsers());
  }

  public static WebBrowserManager getInstance() {
    return ApplicationManager.getApplication().getService(WebBrowserManager.class);
  }

  public static boolean isYandexBrowser(@NotNull WebBrowser browser) {
    return browser.getFamily().equals(BrowserFamily.CHROME) && (browser.getId().equals(PREDEFINED_YANDEX_ID) || checkNameAndPath("Yandex", browser));
  }

  public static boolean isDartium(@NotNull WebBrowser browser) {
    return browser.getFamily().equals(BrowserFamily.CHROME) && checkNameAndPath("Dartium", browser);
  }

  public static boolean isEdge(@NotNull WebBrowser browser) {
    return browser.getFamily() == BrowserFamily.CHROME &&
           (browser.getId().equals(PREDEFINED_EDGE_ID) ||
            checkNameAndPath(getEdgeExecutionPath(), browser) ||
            checkNameAndPath("MicrosoftEdge", browser));
  }

  public static boolean isOpera(@NotNull WebBrowser browser) {
    return checkNameAndPath("Opera", browser);
  }

  static boolean checkNameAndPath(@NotNull String what, @NotNull WebBrowser browser) {
    if (StringUtil.containsIgnoreCase(browser.getName(), what)) {
      return true;
    }
    String path = browser.getPath();
    if (path != null) {
      String fileName = PathUtil.getFileName(path);
      if (StringUtil.containsIgnoreCase(fileName, what)) return true;
      String parentPath = PathUtil.getParentPath(path);
      String parentPathName = PathUtil.getFileName(parentPath);
      if ("bin".equals(parentPathName)) {
        parentPath = PathUtil.getParentPath(parentPath);
        parentPathName = PathUtil.getFileName(parentPath);
      }
      return StringUtil.containsIgnoreCase(parentPathName, what);
    }
    return false;
  }

  boolean isPredefinedBrowser(@NotNull ConfigurableWebBrowser browser) {
    UUID id = browser.getId();
    for (UUID predefinedBrowserId : PREDEFINED_BROWSER_IDS) {
      if (id.equals(predefinedBrowserId)) {
        return true;
      }
    }
    return false;
  }

  public @NotNull DefaultBrowserPolicy getDefaultBrowserPolicy() {
    return defaultBrowserPolicy;
  }

  public @NotNull ReloadMode getWebServerReloadMode() {
    return webServerReloadMode;
  }

  public @NotNull ReloadMode getWebPreviewReloadMode() {
    return webPreviewReloadMode;
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    if (defaultBrowserPolicy != DefaultBrowserPolicy.SYSTEM) {
      state.setAttribute("default", StringUtil.toLowerCase(defaultBrowserPolicy.name()));
    }
    if (webServerReloadMode != BROWSER_RELOAD_MODE_DEFAULT) {
      state.setAttribute("serverReloadMode", StringUtil.toLowerCase(webServerReloadMode.name()));
    }
    if (webPreviewReloadMode != PREVIEW_RELOAD_MODE_DEFAULT) {
      state.setAttribute("previewReloadMode", StringUtil.toLowerCase(webPreviewReloadMode.name()));
    }
    if (!showBrowserHover) {
      state.setAttribute("showHover", "false");
    }
    if (showBrowserHoverXml) {
      state.setAttribute("showHoverXml", "true");
    }

    if (!browsers.equals(getPredefinedBrowsers())) {
      for (ConfigurableWebBrowser browser : browsers) {
        Element entry = new Element("browser");
        entry.setAttribute("id", browser.getId().toString());
        entry.setAttribute("name", browser.getName());
        entry.setAttribute("family", browser.getFamily().name());

        String path = browser.getPath();
        if (path != null && !path.equals(browser.getFamily().getExecutionPath())) {
          entry.setAttribute("path", path);
        }

        if (!browser.isActive()) {
          entry.setAttribute("active", "false");
        }

        BrowserSpecificSettings specificSettings = browser.getSpecificSettings();
        if (specificSettings != null) {
          Element settingsElement = new Element("settings");
          XmlSerializer.serializeInto(specificSettings, settingsElement, new SkipDefaultValuesSerializationFilters());
          if (!JDOMUtil.isEmpty(settingsElement)) {
            entry.addContent(settingsElement);
          }
        }
        state.addContent(entry);
      }
    }
    return state;
  }

  private static @Nullable BrowserFamily readFamily(String value) {
    try {
      return "OPERA".equals(value) ? BrowserFamily.CHROME : BrowserFamily.valueOf(value);
    }
    catch (RuntimeException e) {
      LOG.warn(e);

      for (BrowserFamily family : BrowserFamily.values()) {
        if (family.getName().equalsIgnoreCase(value)) {
          return family;
        }
      }

      return null;
    }
  }

  private static @Nullable UUID readId(String value, @NotNull BrowserFamily family, @NotNull List<ConfigurableWebBrowser> existingBrowsers) {
    if (StringUtil.isEmpty(value)) {
      UUID id;
      switch (family) {
        case CHROME:
          id = PREDEFINED_CHROME_ID;
          break;
        case EXPLORER:
          id = PREDEFINED_EXPLORER_ID;
          break;
        case FIREFOX:
          id = PREDEFINED_FIREFOX_ID;
          break;
        case SAFARI:
          id = PREDEFINED_SAFARI_ID;
          break;

        default:
          return null;
      }

      for (ConfigurableWebBrowser browser : existingBrowsers) {
        if (browser.getId() == id) {
          // duplicated entry, skip
          return null;
        }
      }
      return id;
    }
    else {
      try {
        return UUID.fromString(value);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    return null;
  }

  @Override
  public void loadState(@NotNull Element element) {
    try {
      String defaultValue = element.getAttributeValue("default");
      if (!StringUtil.isEmpty(defaultValue)) {
          defaultBrowserPolicy = DefaultBrowserPolicy.valueOf(StringUtil.toUpperCase(defaultValue));
      }
      String serverReload = element.getAttributeValue("serverReloadMode");
      if (!StringUtil.isEmpty(serverReload)) {
        webServerReloadMode = ReloadMode.valueOf(StringUtil.toUpperCase(serverReload));
      }
      String previewReload = element.getAttributeValue("previewReloadMode");
      if (!StringUtil.isEmpty(previewReload)) {
        webPreviewReloadMode = ReloadMode.valueOf(StringUtil.toUpperCase(previewReload));
      }
    }
    catch (IllegalArgumentException e) {
      LOG.warn(e);
    }

    showBrowserHover = !"false".equals(element.getAttributeValue("showHover"));

    List<ConfigurableWebBrowser> list = new ArrayList<>();
    for (Element child : element.getChildren("browser")) {
      BrowserFamily family = readFamily(child.getAttributeValue("family"));
      if (family == null) {
        continue;
      }

      UUID id = readId(child.getAttributeValue("id"), family, list);
      if (id == null || PREDEFINED_OLD_EDGE_ID.equals(id)) {
        continue;
      }

      Element settingsElement = child.getChild("settings");
      BrowserSpecificSettings specificSettings = family.createBrowserSpecificSettings();
      if (specificSettings != null && settingsElement != null) {
        try {
          XmlSerializer.deserializeInto(specificSettings, settingsElement);
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      }

      String activeValue = child.getAttributeValue("active");

      String path = StringUtil.nullize(child.getAttributeValue("path"), true);
      if (path == null) {
        path = family.getExecutionPath();
      }

      list.add(new ConfigurableWebBrowser(id,
                                          family,
                                          StringUtil.notNullize(child.getAttributeValue("name"), family.getName()),
                                          path,
                                          activeValue == null || Boolean.parseBoolean(activeValue),
                                          specificSettings));
    }

    // add removed/new predefined browsers
    Map<UUID, ConfigurableWebBrowser> idToBrowser = null;
    int n = list.size();
    pb: for (UUID predefinedBrowserId : PREDEFINED_BROWSER_IDS) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < n; i++) {
        if (list.get(i).getId().equals(predefinedBrowserId)) {
          continue pb;
        }
      }

      if (idToBrowser == null) {
        idToBrowser = ContainerUtil.newMapFromValues(getPredefinedBrowsers().iterator(), it -> it.getId());
      }
      list.add(idToBrowser.get(predefinedBrowserId));
    }

    setList(list);
  }

  public @NotNull List<WebBrowser> getBrowsers() {
    return Collections.unmodifiableList(browsers);
  }

  @NotNull
  List<ConfigurableWebBrowser> getList() {
    return browsers;
  }

  void setList(@NotNull List<ConfigurableWebBrowser> value) {
    browsers = value;
    incModificationCount();
  }

  public @NotNull List<WebBrowser> getActiveBrowsers() {
    return getBrowsers(Conditions.alwaysTrue(), true);
  }

  public @NotNull List<WebBrowser> getBrowsers(@NotNull Condition<? super WebBrowser> condition) {
    return getBrowsers(condition, true);
  }

  public @NotNull List<WebBrowser> getBrowsers(@NotNull Condition<? super WebBrowser> condition, boolean onlyActive) {
    List<WebBrowser> result = new SmartList<>();
    for (ConfigurableWebBrowser browser : browsers) {
      if ((!onlyActive || browser.isActive()) && condition.value(browser)) {
        result.add(browser);
      }
    }
    return result;
  }

  public void setBrowserSpecificSettings(@NotNull WebBrowser browser, @NotNull BrowserSpecificSettings specificSettings) {
    ((ConfigurableWebBrowser)browser).setSpecificSettings(specificSettings);
  }

  public void setBrowserPath(@NotNull WebBrowser browser, @Nullable String path, boolean isActive) {
    ((ConfigurableWebBrowser)browser).setPath(path);
    ((ConfigurableWebBrowser)browser).setActive(isActive);
    incModificationCount();
  }

  public WebBrowser addBrowser(final @NotNull UUID id,
                               final @NotNull BrowserFamily family,
                               final @NotNull String name,
                               final @Nullable String path,
                               final boolean active,
                               final BrowserSpecificSettings specificSettings) {
    final ConfigurableWebBrowser browser = new ConfigurableWebBrowser(id, family, name, path, active, specificSettings);
    browsers.add(browser);
    incModificationCount();
    return browser;
  }

  private static @Nullable UUID parseUuid(@NotNull String id) {
    if (id.indexOf('-') == -1) {
      return null;
    }

    try {
      return UUID.fromString(id);
    }
    catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  /**
   * @param idOrFamilyName UUID or, due to backward compatibility, browser family name or JS debugger engine ID
   */
  public @Nullable WebBrowser findBrowserById(@Nullable String idOrFamilyName) {
    if (Strings.isEmpty(idOrFamilyName)) {
      return null;
    }

    UUID id = parseUuid(idOrFamilyName);
    if (id == null) {
      for (ConfigurableWebBrowser browser : browsers) {
        if (browser.getFamily().name().equalsIgnoreCase(idOrFamilyName) ||
            browser.getFamily().getName().equalsIgnoreCase(idOrFamilyName)) {
          return browser;
        }
      }
      return null;
    }

    for (ConfigurableWebBrowser browser : browsers) {
      if (browser.getId().equals(id)) {
        return browser;
      }
    }
    return null;
  }

  public @Nullable WebBrowser getFirstBrowserOrNull(@NotNull BrowserFamily family) {
    for (ConfigurableWebBrowser browser : browsers) {
      if (browser.isActive() && family.equals(browser.getFamily())) {
        return browser;
      }
    }

    for (ConfigurableWebBrowser browser : browsers) {
      if (family.equals(browser.getFamily())) {
        return browser;
      }
    }

    return null;
  }

  public @NotNull WebBrowser getFirstBrowser(@NotNull BrowserFamily family) {
    WebBrowser result = getFirstBrowserOrNull(family);
    if (result == null) {
      throw new IllegalStateException("Must be at least one browser per family");
    }
    return result;
  }

  public boolean isActive(@NotNull WebBrowser browser) {
    return !(browser instanceof ConfigurableWebBrowser) || ((ConfigurableWebBrowser)browser).isActive();
  }

  public @Nullable WebBrowser getFirstActiveBrowser() {
    for (ConfigurableWebBrowser browser : browsers) {
      if (browser.isActive() && browser.getPath() != null) {
        return browser;
      }
    }
    return null;
  }

  public void setShowBrowserHover(boolean showBrowserHover) {
    this.showBrowserHover = showBrowserHover;
  }

  public void setShowBrowserHoverXml(boolean showBrowserHover) {
    showBrowserHoverXml = showBrowserHover;
  }

  public boolean isShowBrowserHover() {
    return showBrowserHover;
  }

  public boolean isShowBrowserHoverXml() {
    return showBrowserHoverXml;
  }
}