// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.ide.GeneralLocalSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@State(name = "WebBrowsersConfiguration",
  category = SettingsCategory.TOOLS,
  exportable = true,
  storages = @Storage(value = "web-browsers.xml", roamingType = RoamingType.DISABLED))
public final class WebBrowserManager extends SimpleModificationTracker implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(WebBrowserManager.class);

  // default standard browser ID must be constant across all IDE versions on all machines for all users
  @ApiStatus.Internal
  public static final UUID PREDEFINED_CHROME_ID = UUID.fromString("98CA6316-2F89-46D9-A9E5-FA9E2B0625B3");
  @ApiStatus.Internal
  public static final UUID PREDEFINED_FIREFOX_ID = UUID.fromString("A7BB68E0-33C0-4D6F-A81A-AAC1FDB870C8");
  private static final UUID PREDEFINED_SAFARI_ID = UUID.fromString("E5120D43-2C3F-47EF-9F26-65E539E05186");
  private static final UUID PREDEFINED_OPERA_ID = UUID.fromString("53E2F627-B1A7-4DFA-BFA7-5B83CC034776");
  private static final UUID PREDEFINED_OLD_EDGE_ID = UUID.fromString("B2A9DCA7-9D0B-4E1E-98A8-AFB19C1328D2");
  private static final UUID PREDEFINED_OLD_EXPLORER_ID = UUID.fromString("16BF23D4-93E0-4FFC-BFD6-CB13575177B0");
  private static final UUID PREDEFINED_EDGE_ID = UUID.fromString("37cae5b9-e8b2-4949-9172-aafa37fbc09c");

  private static final UUID[] PREDEFINED_BROWSER_IDS = new UUID[]{
    PREDEFINED_CHROME_ID,
    PREDEFINED_FIREFOX_ID,
    PREDEFINED_SAFARI_ID,
    PREDEFINED_OPERA_ID,
    PREDEFINED_EDGE_ID
  };

  public static final ReloadMode BROWSER_RELOAD_MODE_DEFAULT = ReloadMode.RELOAD_ON_SAVE;
  public static final ReloadMode PREVIEW_RELOAD_MODE_DEFAULT = ReloadMode.RELOAD_ON_SAVE;

  private static final String BROWSER_TAG = "browser";
  private static final String BROWSERS_TAG = "browsers";
  private static final String ACTIVE_TAG = "active";
  private static final String INACTIVE_TAG = "inactive";

  /**
   * The order of the inactive browsers: the predefined browsers in their default order, then the custom browsers by name.
   */
  private static final Comparator<ConfigurableWebBrowser> INACTIVE_ORDER =
    Comparator.comparingInt((ConfigurableWebBrowser browser) -> {
        int index = ArrayUtil.indexOf(PREDEFINED_BROWSER_IDS, browser.getId());
        return index < 0 ? Integer.MAX_VALUE : index;
      })
      .thenComparing(ConfigurableWebBrowser::getName, String.CASE_INSENSITIVE_ORDER)
      .thenComparing(browser -> browser.getId().toString());

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
    return List.of(
      new ConfigurableWebBrowser(PREDEFINED_CHROME_ID, BrowserFamily.CHROME),
      new ConfigurableWebBrowser(PREDEFINED_FIREFOX_ID, BrowserFamily.FIREFOX),
      new ConfigurableWebBrowser(PREDEFINED_SAFARI_ID, BrowserFamily.SAFARI, BrowserFamily.SAFARI.getName(),
                                 BrowserFamily.SAFARI.getExecutionPath(), SystemInfo.isMac,
                                 BrowserFamily.SAFARI.createBrowserSpecificSettings()),
      new ConfigurableWebBrowser(PREDEFINED_OPERA_ID, BrowserFamily.CHROME, "Opera", SystemInfo.isMac ? "Opera" : "opera", false,
                                 BrowserFamily.CHROME.createBrowserSpecificSettings()),
      new ConfigurableWebBrowser(PREDEFINED_EDGE_ID, BrowserFamily.CHROME, "Edge",
                                 getEdgeExecutionPath(), SystemInfo.isWindows,
                                 BrowserFamily.CHROME.createBrowserSpecificSettings())
    );
  }

  private List<ConfigurableWebBrowser> browsers;
  private boolean showBrowserHover = true;
  private boolean showBrowserHoverXml = false;
  /** The id of the default browser of the list, or {@code null} for {@link SystemDefaultBrowser}. */
  private @Nullable UUID defaultBrowserId;
  ReloadMode webServerReloadMode = BROWSER_RELOAD_MODE_DEFAULT;
  ReloadMode webPreviewReloadMode = PREVIEW_RELOAD_MODE_DEFAULT;

  public WebBrowserManager() {
    browsers = new ArrayList<>(getPredefinedBrowsers());
  }

  public static WebBrowserManager getInstance() {
    return ApplicationManager.getApplication().getService(WebBrowserManager.class);
  }

  public static boolean isYandexBrowser(@NotNull WebBrowser browser) {
    return browser.getFamily() == BrowserFamily.CHROME && checkNameAndPath("Yandex", browser);
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
    return ArrayUtil.contains(id, PREDEFINED_BROWSER_IDS);
  }

  /**
   * @deprecated The default browser is a browser now, see {@link #getDefaultBrowser()}.
   * This method returns {@link DefaultBrowserPolicy#SYSTEM} for a browser without a path, such as {@link SystemDefaultBrowser}.
   * For a browser with a path, it returns {@link DefaultBrowserPolicy#FIRST}, but the browser is not always the first one.
   */
  @Deprecated
  public @NotNull DefaultBrowserPolicy getDefaultBrowserPolicy() {
    return getDefaultBrowser().getPath() == null ? DefaultBrowserPolicy.SYSTEM : DefaultBrowserPolicy.FIRST;
  }

  /**
   * Returns the browser that opens a URL when the caller names no browser.
   * It is the browser that the developer picked, found as {@link #findBrowserById(String)} finds it.
   * A browser of the list must be active and have a path.
   * Otherwise, it is {@link SystemDefaultBrowser}.
   */
  @ApiStatus.Internal
  public @NotNull WebBrowser getDefaultBrowser() {
    WebBrowser browser = defaultBrowserId == null ? null : findBrowserById(defaultBrowserId);
    if (browser == null ||
        browser instanceof ConfigurableWebBrowser configurable && (!configurable.isActive() || configurable.getPath() == null)) {
      return SystemDefaultBrowser.INSTANCE;
    }
    return browser;
  }

  /**
   * Returns the id of the default browser that the developer picked, or {@code null} for {@link SystemDefaultBrowser}.
   * {@link #getDefaultBrowser()} resolves it.
   */
  @ApiStatus.Internal
  public @Nullable UUID getDefaultBrowserId() {
    return defaultBrowserId;
  }

  /**
   * Stores the default browser. {@code null} and the id of {@link SystemDefaultBrowser} store the system default browser.
   */
  @ApiStatus.Internal
  public void setDefaultBrowserId(@Nullable UUID id) {
    defaultBrowserId = SystemDefaultBrowser.INSTANCE.getId().equals(id) ? null : id;
    syncGeneralLocalSettings();
    incModificationCount();
  }

  /**
   * Copies the default browser to {@link GeneralLocalSettings}.
   * The launcher reads these settings before the components load, and an older IDE version reads them too.
   * The copy also stops a second migration of the custom command, see {@link #migrateDefaultBrowser(String)}.
   */
  private void syncGeneralLocalSettings() {
    GeneralLocalSettings settings = GeneralLocalSettings.getInstance();
    // the older code opens a browser without a path, such as a provided one, as the system default browser
    String path = getDefaultBrowser().getPath();
    settings.setUseDefaultBrowser(path == null);
    if (path != null) {
      settings.setBrowserPath(path);
    }
  }

  public @NotNull ReloadMode getWebServerReloadMode() {
    return webServerReloadMode;
  }

  public @NotNull ReloadMode getWebPreviewReloadMode() {
    return webPreviewReloadMode;
  }

  @Override
  public @NotNull Element getState() {
    Element state = new Element("state");
    if (defaultBrowserId != null) {
      state.setAttribute("defaultBrowser", defaultBrowserId.toString());
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

    writeBrowsers(state);
    return state;
  }

  /**
   * Writes only the differences from the predefined browsers.
   * {@code active} is the ordered list of active browsers, written only when it differs from the default.
   * {@code inactive} lists the predefined browsers that are active by default but the user turned off.
   * {@code browsers} holds the custom browsers and the changed attributes of the predefined browsers.
   */
  private void writeBrowsers(@NotNull Element state) {
    List<ConfigurableWebBrowser> predefinedBrowsers = getPredefinedBrowsers();
    Map<UUID, ConfigurableWebBrowser> idToPredefined = ContainerUtil.newMapFromValues(predefinedBrowsers.iterator(), it -> it.getId());

    List<UUID> activeIds = new ArrayList<>();
    List<UUID> inactiveIds = new ArrayList<>();
    for (ConfigurableWebBrowser browser : browsers) {
      if (browser.isActive()) {
        activeIds.add(browser.getId());
      }
      else {
        ConfigurableWebBrowser predefined = idToPredefined.get(browser.getId());
        if (predefined != null && predefined.isActive()) {
          inactiveIds.add(browser.getId());
        }
      }
    }

    if (!activeIds.equals(getDefaultActiveIds(predefinedBrowsers, inactiveIds))) {
      state.addContent(writeIds(ACTIVE_TAG, activeIds));
    }
    if (!inactiveIds.isEmpty()) {
      inactiveIds.sort(Comparator.comparing(UUID::toString));
      state.addContent(writeIds(INACTIVE_TAG, inactiveIds));
    }

    Element definitions = new Element(BROWSERS_TAG);
    List<ConfigurableWebBrowser> sortedBrowsers = new ArrayList<>(browsers);
    sortedBrowsers.sort(INACTIVE_ORDER);
    for (ConfigurableWebBrowser browser : sortedBrowsers) {
      Element entry = writeBrowser(browser, idToPredefined.get(browser.getId()));
      if (entry != null) {
        definitions.addContent(entry);
      }
    }
    if (!definitions.getChildren().isEmpty()) {
      state.addContent(definitions);
    }
  }

  private static @NotNull List<UUID> getDefaultActiveIds(@NotNull List<ConfigurableWebBrowser> predefinedBrowsers,
                                                         @NotNull Collection<UUID> inactiveIds) {
    List<UUID> result = new ArrayList<>();
    for (ConfigurableWebBrowser browser : predefinedBrowsers) {
      if (browser.isActive() && !inactiveIds.contains(browser.getId())) {
        result.add(browser.getId());
      }
    }
    return result;
  }

  private static @NotNull Element writeIds(@NotNull String tagName, @NotNull List<UUID> ids) {
    Element element = new Element(tagName);
    for (UUID id : ids) {
      element.addContent(new Element(BROWSER_TAG).setAttribute("id", id.toString()));
    }
    return element;
  }

  /**
   * @return the full entry for a custom browser, or only the changed attributes for a predefined browser,
   * or {@code null} if the predefined browser has no changes
   */
  private static @Nullable Element writeBrowser(@NotNull ConfigurableWebBrowser browser, @Nullable ConfigurableWebBrowser predefined) {
    Element entry = new Element(BROWSER_TAG);
    entry.setAttribute("id", browser.getId().toString());
    if (predefined == null) {
      entry.setAttribute("name", browser.getName());
      if (browser.getFamily() != null) {
        entry.setAttribute("family", browser.getFamily().name());
      }
      if (browser.getPath() != null) {
        entry.setAttribute("path", browser.getPath());
      }
    }
    else {
      if (!browser.getName().equals(predefined.getName())) {
        entry.setAttribute("name", browser.getName());
      }
      if (!Objects.equals(browser.getPath(), predefined.getPath())) {
        // an empty path means that the user cleared the predefined path
        entry.setAttribute("path", StringUtil.notNullize(browser.getPath()));
      }
    }

    BrowserSpecificSettings specificSettings = browser.getSpecificSettings();
    if (specificSettings != null) {
      Element settingsElement = new Element("settings");
      XmlSerializer.serializeInto(specificSettings, settingsElement, new SkipDefaultValuesSerializationFilters());
      if (!JDOMUtil.isEmpty(settingsElement)) {
        entry.addContent(settingsElement);
      }
    }

    return predefined == null || entry.getAttributes().size() > 1 || !entry.getChildren().isEmpty() ? entry : null;
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

  private static @Nullable UUID readId(String value,
                                       @Nullable BrowserFamily family,
                                       @NotNull List<ConfigurableWebBrowser> existingBrowsers) {
    if (StringUtil.isEmpty(value)) {
      if (family == null) {
        return null;
      }

      UUID id;
      switch (family) {
        case CHROME -> id = PREDEFINED_CHROME_ID;
        case FIREFOX -> id = PREDEFINED_FIREFOX_ID;
        case SAFARI -> id = PREDEFINED_SAFARI_ID;
        default -> {
          return null;
        }
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

    String defaultBrowser = element.getAttributeValue("defaultBrowser");
    defaultBrowserId = defaultBrowser == null ? null : parseUuid(defaultBrowser);
    if (SystemDefaultBrowser.INSTANCE.getId().equals(defaultBrowserId)) {
      defaultBrowserId = null;
    }

    showBrowserHover = !"false".equals(element.getAttributeValue("showHover"));
    showBrowserHoverXml = "true".equals(element.getAttributeValue("showHoverXml"));

    setList(element.getChildren(BROWSER_TAG).isEmpty() ? readBrowsers(element) : readLegacyBrowsers(element));
    if (defaultBrowser == null) {
      migrateDefaultBrowser(element.getAttributeValue("default"));
    }
  }

  @Override
  public void noStateLoaded() {
    migrateDefaultBrowser(null);
  }

  private static @NotNull List<ConfigurableWebBrowser> readBrowsers(@NotNull Element element) {
    List<ConfigurableWebBrowser> predefinedBrowsers = getPredefinedBrowsers();
    Map<UUID, ConfigurableWebBrowser> idToBrowser = new LinkedHashMap<>();
    for (ConfigurableWebBrowser browser : predefinedBrowsers) {
      idToBrowser.put(browser.getId(), browser);
    }

    Element definitions = element.getChild(BROWSERS_TAG);
    if (definitions != null) {
      for (Element child : definitions.getChildren(BROWSER_TAG)) {
        UUID id = parseUuid(StringUtil.notNullize(child.getAttributeValue("id")));
        if (id == null || PREDEFINED_OLD_EDGE_ID.equals(id)) {
          continue;
        }

        ConfigurableWebBrowser browser = idToBrowser.get(id);
        if (browser == null) {
          ContainerUtil.putIfNotNull(id, readCustomBrowser(id, child), idToBrowser);
        }
        else if (ArrayUtil.contains(id, PREDEFINED_BROWSER_IDS)) {
          readPredefinedBrowser(browser, child);
        }
      }
    }

    List<UUID> inactiveIds = readIds(element.getChild(INACTIVE_TAG));
    Element activeElement = element.getChild(ACTIVE_TAG);
    Set<UUID> activeIds = new LinkedHashSet<>(activeElement == null ? List.of() : readIds(activeElement));
    // a predefined browser that is active by default and is in neither list goes to the end of the active list
    activeIds.addAll(getDefaultActiveIds(predefinedBrowsers, inactiveIds));

    List<ConfigurableWebBrowser> result = new ArrayList<>(idToBrowser.size());
    for (UUID id : activeIds) {
      ConfigurableWebBrowser browser = idToBrowser.remove(id);
      if (browser != null) {
        browser.setActive(true);
        result.add(browser);
      }
    }
    for (ConfigurableWebBrowser browser : idToBrowser.values()) {
      browser.setActive(false);
      result.add(browser);
    }
    return result;
  }

  private static @NotNull List<UUID> readIds(@Nullable Element element) {
    if (element == null) {
      return List.of();
    }

    List<UUID> result = new ArrayList<>();
    for (Element child : element.getChildren(BROWSER_TAG)) {
      UUID id = parseUuid(StringUtil.notNullize(child.getAttributeValue("id")));
      if (id != null) {
        result.add(id);
      }
    }
    return result;
  }

  private static void readPredefinedBrowser(@NotNull ConfigurableWebBrowser browser, @NotNull Element element) {
    String name = StringUtil.nullize(element.getAttributeValue("name"), true);
    if (name != null) {
      browser.setName(name);
    }
    String path = element.getAttributeValue("path");
    if (path != null) {
      browser.setPath(path);
    }
    BrowserSpecificSettings specificSettings = readSpecificSettings(browser.getFamily(), element);
    if (specificSettings != null) {
      browser.setSpecificSettings(specificSettings);
    }
  }

  private static @Nullable ConfigurableWebBrowser readCustomBrowser(@NotNull UUID id, @NotNull Element element) {
    String familyValue = element.getAttributeValue("family");
    BrowserFamily family = familyValue == null ? null : readFamily(familyValue);
    if (familyValue != null && family == null) {
      return null;
    }

    BrowserSpecificSettings specificSettings = readSpecificSettings(family, element);
    return new ConfigurableWebBrowser(id,
                                      family,
                                      StringUtil.notNullize(element.getAttributeValue("name"), family == null ? "" : family.getName()),
                                      StringUtil.nullize(element.getAttributeValue("path"), true),
                                      false,
                                      specificSettings == null && family != null
                                      ? family.createBrowserSpecificSettings()
                                      : specificSettings);
  }

  private static @Nullable BrowserSpecificSettings readSpecificSettings(@Nullable BrowserFamily family, @NotNull Element element) {
    Element settingsElement = element.getChild("settings");
    if (family == null || settingsElement == null) {
      return null;
    }

    BrowserSpecificSettings specificSettings = family.createBrowserSpecificSettings();
    if (specificSettings != null) {
      try {
        XmlSerializer.deserializeInto(specificSettings, settingsElement);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    return specificSettings;
  }

  /**
   * Reads the format where each browser is a direct child of the state and has an {@code active} attribute.
   */
  private static @NotNull List<ConfigurableWebBrowser> readLegacyBrowsers(@NotNull Element element) {
    List<ConfigurableWebBrowser> list = new ArrayList<>();
    for (Element child : element.getChildren(BROWSER_TAG)) {
      String familyValue = child.getAttributeValue("family");
      if ("EXPLORER".equals(familyValue)) {
        // Internet Explorer is no longer supported, so a custom browser of this family keeps no family
        familyValue = null;
      }
      BrowserFamily family = familyValue == null ? null : readFamily(familyValue);
      if (familyValue != null && family == null) {
        continue;
      }

      UUID id = readId(child.getAttributeValue("id"), family, list);
      if (id == null || PREDEFINED_OLD_EDGE_ID.equals(id) || PREDEFINED_OLD_EXPLORER_ID.equals(id)) {
        continue;
      }

      Element settingsElement = child.getChild("settings");
      BrowserSpecificSettings specificSettings = family == null ? null : family.createBrowserSpecificSettings();
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
      if (path == null && family != null) {
        path = family.getExecutionPath();
      }

      list.add(new ConfigurableWebBrowser(id,
                                          family,
                                          StringUtil.notNullize(child.getAttributeValue("name"), family == null ? "" : family.getName()),
                                          path,
                                          activeValue == null || Boolean.parseBoolean(activeValue),
                                          specificSettings));
    }

    // add removed/new predefined browsers
    Map<UUID, ConfigurableWebBrowser> idToBrowser = null;
    int n = list.size();
    pb:
    for (UUID predefinedBrowserId : PREDEFINED_BROWSER_IDS) {
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

    return list;
  }

  /**
   * Picks the default browser that the old settings open, when the state names no default browser.
   * The old settings are the {@code default} policy of the state and the flag of {@link GeneralLocalSettings#getUseDefaultBrowser()}.
   * <p>
   * The old launcher opened the first active browser for the {@code FIRST} policy.
   * For another policy, it followed the flag: the system default browser, or the custom command without the flag.
   * The policy and the flag can disagree after an import of the settings, so the flag decides here, as in the old launcher.
   */
  private void migrateDefaultBrowser(@Nullable String legacyPolicy) {
    if ("first".equalsIgnoreCase(legacyPolicy)) {
      useFirstActiveBrowser();
    }
    else if (!GeneralLocalSettings.getInstance().getUseDefaultBrowser()) {
      migrateCustomCommand();
    }
    else {
      return;
    }
    syncGeneralLocalSettings();
  }

  private void useFirstActiveBrowser() {
    WebBrowser firstActiveBrowser = getFirstActiveBrowser();
    defaultBrowserId = firstActiveBrowser == null ? null : firstActiveBrowser.getId();
  }

  /**
   * Makes the custom command of {@link GeneralLocalSettings#getBrowserPath()} a browser of the list, and makes it the default.
   * It reuses a browser that the command names by id or by family, or an active browser with the same path.
   * Without a command, or with an Internet Explorer command, the default becomes {@link SystemDefaultBrowser}.
   * The macOS {@code open} command gives the first active browser, because the old launcher opened that browser for it.
   */
  private void migrateCustomCommand() {
    String path = StringUtil.nullize(GeneralLocalSettings.getInstance().getBrowserPath(), true);
    if (path == null || StringUtil.containsIgnoreCase(PathUtil.getFileName(path), "iexplore")) {
      defaultBrowserId = null;
      return;
    }
    if (SystemInfo.isMac && "open".equals(path)) {
      useFirstActiveBrowser();
      return;
    }
    WebBrowser browser = findBrowserById(path);
    if (browser == null) {
      browser = ContainerUtil.find(getActiveBrowsers(), it -> path.equals(it.getPath()));
    }
    if (browser == null) {
      BrowserFamily family = guessFamily(path);
      browser = addBrowser(UUID.randomUUID(), family, PathUtil.getFileName(path), path, true,
                           family == null ? null : family.createBrowserSpecificSettings());
    }
    defaultBrowserId = browser == SystemDefaultBrowser.INSTANCE ? null : browser.getId();
  }

  /**
   * @return the family that the file name of the command names, or {@code null} for an unknown program
   */
  private static @Nullable BrowserFamily guessFamily(@NotNull String path) {
    String fileName = StringUtil.toLowerCase(PathUtil.getFileName(path));
    if (fileName.contains("firefox")) return BrowserFamily.FIREFOX;
    if (fileName.contains("safari")) return BrowserFamily.SAFARI;
    for (String chromium : List.of("chrome", "chromium", "edge", "opera", "brave", "vivaldi", "yandex")) {
      if (fileName.contains(chromium)) return BrowserFamily.CHROME;
    }
    return null;
  }

  public @NotNull List<WebBrowser> getBrowsers() {
    return Collections.unmodifiableList(browsers);
  }

  @NotNull
  List<ConfigurableWebBrowser> getList() {
    return browsers;
  }

  /**
   * Puts the active browsers first, in their order, and the inactive browsers after them, in {@link #INACTIVE_ORDER}.
   */
  void setList(@NotNull List<ConfigurableWebBrowser> value) {
    List<ConfigurableWebBrowser> list = new ArrayList<>(value.size());
    List<ConfigurableWebBrowser> inactive = new ArrayList<>();
    for (ConfigurableWebBrowser browser : value) {
      (browser.isActive() ? list : inactive).add(browser);
    }
    inactive.sort(INACTIVE_ORDER);
    list.addAll(inactive);
    browsers = list;
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
                               final @Nullable BrowserFamily family,
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
   * Checks {@link SystemDefaultBrowser}, then the browsers of the list, then the browsers of a {@link WebBrowserProvider}.
   * So a provider cannot take the id of the system default browser or of a browser of the list.
   *
   * @param idOrFamilyName UUID or, due to backward compatibility, browser family name or JS debugger engine ID
   */
  public @Nullable WebBrowser findBrowserById(@Nullable String idOrFamilyName) {
    if (Strings.isEmpty(idOrFamilyName)) {
      return null;
    }

    UUID id = parseUuid(idOrFamilyName);
    if (id != null) {
      return findBrowserById(id);
    }

    for (ConfigurableWebBrowser browser : browsers) {
      BrowserFamily family = browser.getFamily();
      if (family != null && (family.name().equalsIgnoreCase(idOrFamilyName) || family.getName().equalsIgnoreCase(idOrFamilyName))) {
        return browser;
      }
    }
    return null;
  }

  private @Nullable WebBrowser findBrowserById(@NotNull UUID id) {
    if (SystemDefaultBrowser.INSTANCE.getId().equals(id)) {
      return SystemDefaultBrowser.INSTANCE;
    }
    for (ConfigurableWebBrowser browser : browsers) {
      if (browser.getId().equals(id)) {
        return browser;
      }
    }
    return ContainerUtil.find(WebBrowserProvider.getAvailableBrowsers(), it -> it.getId().equals(id));
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