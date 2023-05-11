// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.fasterxml.jackson.jr.ob.JSON;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.ColorHexUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.Gray;
import com.intellij.ui.icons.ImageDataByPathLoader;
import com.intellij.ui.icons.ImageDataByPathLoaderKt;
import com.intellij.ui.svg.SvgAttributePatcher;
import com.intellij.ui.svg.SvgKt;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.InsecureHashBuilder;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Function;

/**
 * @author Konstantin Bulenkov
 */
public final class UITheme {
  public static final String FILE_EXT_ENDING = ".theme.json";

  private static final Logger LOG = Logger.getInstance(UITheme.class);

  private String name;
  private boolean dark;
  private String author;
  private String id;
  private String editorScheme;
  private @Nullable String parentTheme;
  private String[] additionalEditorSchemes;
  private Map<String, Object> ui;
  private @Nullable Map<String, Object> icons;
  private @Nullable IconPathPatcher patcher;
  private Map<String, Object> background;
  private Map<String, Object> emptyFrameBackground;
  private @Nullable Map<String, Object> colors;
  private @Nullable Map<String, Object> iconColorsOnSelection;
  private ClassLoader providerClassLoader = getClass().getClassLoader();
  private String editorSchemeName;
  private SVGLoader.SvgElementColorPatcherProvider colorPatcher;
  private SVGLoader.SvgElementColorPatcherProvider selectionColorPatcher;

  private static final String OS_MACOS_KEY = "os.mac";
  private static final String OS_WINDOWS_KEY = "os.windows";
  private static final String OS_LINUX_KEY = "os.linux";
  private static final String OS_DEFAULT_KEY = "os.default";

  private UITheme() { }

  public String getName() {
    return name;
  }

  public boolean isDark() {
    return dark;
  }

  public String getAuthor() {
    return author;
  }

  // it caches classes - must be not extracted to util class
  // .disable(JSON.Feature.PRESERVE_FIELD_ORDERING) - cannot be disabled, for unknown reason order is important
  // for example, button label font color for light theme is not white, but black
  private static final JSON JSON_READER = JSON.builder()
    .enable(JSON.Feature.READ_ONLY)
    .build();

  public static @NotNull UITheme loadFromJson(@NotNull InputStream stream,
                                              @NotNull @NonNls String themeId,
                                              @Nullable ClassLoader provider,
                                              @NotNull Function<? super String, String> iconsMapper) throws IOException {
    UITheme theme = JSON_READER.beanFrom(UITheme.class, stream);
    theme.id = themeId;
    return postProcessTheme(theme, findParentTheme(theme), provider, iconsMapper);
  }

  public static @NotNull UITheme loadFromJson(byte[] data,
                                              @NotNull @NonNls String themeId,
                                              @Nullable ClassLoader provider,
                                              @NotNull Function<? super String, String> iconsMapper) throws IOException {
    UITheme theme = JSON_READER.beanFrom(UITheme.class, data);
    theme.id = themeId;
    return postProcessTheme(theme, findParentTheme(theme), provider, iconsMapper);
  }

  public static @NotNull UITheme loadFromJson(@Nullable UITheme parentTheme,
                                              byte[] data,
                                              @NotNull @NonNls String themeId,
                                              @Nullable ClassLoader provider,
                                              @NotNull Function<? super String, String> iconsMapper) throws IOException {
    UITheme theme = JSON_READER.beanFrom(UITheme.class, data);
    theme.id = themeId;
    return postProcessTheme(theme, parentTheme, provider, iconsMapper);
  }

  private static @NotNull UITheme postProcessTheme(@NotNull UITheme theme,
                                                   @Nullable UITheme parentTheme,
                                                   @Nullable ClassLoader provider,
                                                   @NotNull Function<? super String, String> iconsMapper) throws IllegalStateException {
    normalizeKeyPaths(theme);
    if (parentTheme != null) {
      importFromParentTheme(theme, parentTheme);
    }
    putDefaultsIfAbsent(theme);
    return loadFromJson(theme, provider, iconsMapper);
  }

  private static @Nullable UITheme findParentTheme(@NotNull UITheme theme) {
    String parentTheme = theme.parentTheme;
    if (parentTheme != null) {
      for (UIManager.LookAndFeelInfo laf : LafManager.getInstance().getInstalledLookAndFeels()) {
        if (laf instanceof UIThemeBasedLookAndFeelInfo) {
          UITheme uiTheme = ((UIThemeBasedLookAndFeelInfo)laf).getTheme();
          if (uiTheme.getName().equals(parentTheme)) {
            return uiTheme;
          }
        }
      }
    }
    return null;
  }

  /**
   * Flatten
   * <pre>{@code "Editor": { "SearchField" : { "borderInsets" : "7,10,7,8" } }}</pre>
   * to
   * <pre>{@code "Editor.SearchField.borderInsets" : "7,10,7,8"}</pre>
   * in internal representation.
   * <p>
   * We also resolve per-OS keys here:
   * <pre> {@code
   *  "Menu.borderColor": {
   *      "os.default": "Grey12",
   *      "os.windows": "Blue12"
   *  }
   * }</pre>
   * <p>
   * This is helpful when we need to check if some key was already set in {@link #putDefaultsIfAbsent},
   * and to make overriding parentTheme keys independent of used form.
   * <p>
   * NB: we intentionally do not expand "*" patterns here.
   */
  private static void normalizeKeyPaths(@NotNull UITheme theme) {
    if (theme.ui == null) return;

    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : theme.ui.entrySet()) {
      normalizeKeyValue(entry.getKey(), entry.getValue(), result);
    }
    theme.ui = result;
  }

  private static void normalizeKeyValue(@NotNull String keyPrefix,
                                        @NotNull Object value,
                                        @NotNull Map<String, Object> result) {
    if (value instanceof Map) {
      @SuppressWarnings("unchecked") Map<String, Object> valueMap = (Map<String, Object>)value;
      if (isOSCustomization(valueMap)) {
        Object osValue = getOSCustomization(valueMap);
        if (osValue != null) {
          normalizeKeyValue(keyPrefix, osValue, result);
        }
      }
      else {
        for (Map.Entry<String, Object> entry : valueMap.entrySet()) {
          String uiKey = createUIKey(keyPrefix, entry.getKey());
          normalizeKeyValue(uiKey, entry.getValue(), result);
        }
      }
    }
    else {
      result.put(keyPrefix, value);
    }
  }

  private static void importFromParentTheme(@NotNull UITheme theme, @NotNull UITheme parentTheme) {
    theme.ui = importMapFromParentTheme(theme.ui, parentTheme.ui);
    theme.icons = importMapFromParentTheme(theme.icons, parentTheme.icons);
    theme.background = importMapFromParentTheme(theme.background, parentTheme.background);
    theme.emptyFrameBackground = importMapFromParentTheme(theme.emptyFrameBackground, parentTheme.emptyFrameBackground);
    theme.colors = importMapFromParentTheme(theme.colors, parentTheme.colors);
    theme.iconColorsOnSelection = importMapFromParentTheme(theme.iconColorsOnSelection, parentTheme.iconColorsOnSelection);
  }

  private static @Nullable Map<String, Object> importMapFromParentTheme(@Nullable Map<String, Object> themeMap,
                                                                        @Nullable Map<String, Object> parentThemeMap) {
    if (parentThemeMap == null) return themeMap;
    Map<String, Object> result = new LinkedHashMap<>(parentThemeMap);
    if (themeMap != null) {
      for (Map.Entry<String, Object> entry : themeMap.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        result.remove(key);
        result.put(key, value);
      }
    }
    return result;
  }

  /**
   * Ensure that the old themes are not missing some vital keys.
   * <p>
   * We are patching them here instead of using {@link com.intellij.ui.JBColor#namedColor} fallback
   * to make sure {@link UIManager#getColor} works properly.
   */
  private static void putDefaultsIfAbsent(@NotNull UITheme theme) {
    if (theme.ui == null) theme.ui = new LinkedHashMap<>();

    if (ExperimentalUI.isNewUI()) {
      theme.ui.putIfAbsent("EditorTabs.underlineArc", "4");

      // require theme to specify ToolWindow stripe button colors explicitly, without "*"
      theme.ui.putIfAbsent("ToolWindow.Button.selectedBackground", "#3573F0");
      theme.ui.putIfAbsent("ToolWindow.Button.selectedForeground", "#FFFFFF");
    }
  }

  private static @NotNull UITheme loadFromJson(@NotNull UITheme theme,
                                               @Nullable ClassLoader provider,
                                               @NotNull Function<? super String, String> iconsMapper) throws IllegalStateException {
    if (provider != null) {
      theme.providerClassLoader = provider;
    }

    initializeNamedColors(theme);
    PaletteScopeManager paletteScopeManager = new PaletteScopeManager();

    Map<String, Object> colorsOnSelection = theme.iconColorsOnSelection;
    if (colorsOnSelection != null && !colorsOnSelection.isEmpty()) {
      Map<String, String> colors = new HashMap<>(colorsOnSelection.size());
      Set<String> alphaColors = new HashSet<>(colorsOnSelection.size());
      for (Map.Entry<String, Object> entry : colorsOnSelection.entrySet()) {
        String value = entry.getValue().toString();
        colors.put(entry.getKey(), value);
        alphaColors.add(value);
      }

      theme.selectionColorPatcher = new SVGLoader.SvgElementColorPatcherProvider() {
        @Override
        public @Nullable SvgAttributePatcher attributeForPath(@Nullable String path) {
          PaletteScope scope = paletteScopeManager.getScopeByPath(path);
          InsecureHashBuilder hash = new InsecureHashBuilder()
            .stringMap(colors)
            .update(scope == null ? ArrayUtilRt.EMPTY_LONG_ARRAY : scope.digest());
          return SvgKt.newSvgPatcher(hash.build(), colors, color -> alphaColors.contains(color) ? 255 : null);
        }
      };
    }

    if (theme.icons != null && !theme.icons.isEmpty()) {
      theme.patcher = new IconPathPatcher() {
        @Override
        public @Nullable String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
          if (classLoader instanceof PluginAwareClassLoader) {
            String pluginId = ((PluginAwareClassLoader)classLoader).getPluginId().getIdString();
            Object icons = theme.icons.get(pluginId);
            if (icons instanceof Map) {
              @SuppressWarnings("unchecked")
              Object pluginIconPath = ((Map<String, Object>)icons).get(path);
              if (pluginIconPath instanceof String) {
                return iconsMapper.apply((String)pluginIconPath);
              }
            }
          }

          Object value = theme.icons.get(path);
          if (value == null && path.charAt(0) != '/') {
            value = theme.icons.get('/' + path);
          }
          return value instanceof String ? iconsMapper.apply((String)value) : null;
        }

        @Override
        public @Nullable ClassLoader getContextClassLoader(@NotNull String path, @Nullable ClassLoader originalClassLoader) {
          return theme.providerClassLoader;
        }
      };

      Object palette = theme.icons.get("ColorPalette");
      if (palette instanceof @SuppressWarnings("rawtypes")Map colors) {
        for (Object o : colors.keySet()) {
          String colorKey = o.toString();
          PaletteScope scope = paletteScopeManager.getScope(colorKey);
          if (scope == null) {
            continue;
          }

          String key = toColorString(colorKey, theme.isDark());
          Object v = colors.get(colorKey);
          if (v instanceof String value) {
            Object namedColor = theme.colors != null ? theme.colors.get(value) : null;
            if (namedColor instanceof String) {
              value = (String)namedColor;
            }
            String alpha = null;
            if (value.length() == 9) {
              alpha = value.substring(7);
              value = value.substring(0, 7);
            }
            if (ColorHexUtil.fromHex(key, null) != null && ColorHexUtil.fromHex(value, null) != null) {
              scope.newPalette.put(key, value);
              int fillTransparency = -1;
              if (alpha != null) {
                try {
                  fillTransparency = Integer.parseInt(alpha, 16);
                }
                catch (Exception ignore) {
                }
              }
              if (fillTransparency != -1) {
                scope.alphas.put(value, fillTransparency);
              }
            }
          }
        }

        theme.colorPatcher = new SVGLoader.SvgElementColorPatcherProvider() {
          @Override
          public @Nullable SvgAttributePatcher attributeForPath(@Nullable String path) {
            PaletteScope scope = paletteScopeManager.getScopeByPath(path);
            return scope == null ? null : SvgKt.newSvgPatcher(scope.digest(), scope.newPalette, scope.alphas::get);
          }
        };
      }
    }

    return theme;
  }

  private static void initializeNamedColors(UITheme theme) {
    Map<String, Object> map = theme.colors;
    if (map == null) {
      return;
    }

    Set<String> namedColors = map.keySet();
    for (String key : namedColors) {
      Object value = map.get(key);
      if (value instanceof String && !((String)value).startsWith("#")) {
        Object delegateColor = map.get(value);
        if (delegateColor != null) {
          map.put(key, delegateColor);
        }
        else {
          LOG.warn("Can't parse '" + value + "' for key '" + key + "'");
          map.put(key, Gray.TRANSPARENT);
        }
      }
    }

    if (theme.iconColorsOnSelection != null) {
      HashSet<Map.Entry<String, Object>> entries = new HashSet<>(theme.iconColorsOnSelection.entrySet());
      theme.iconColorsOnSelection.clear();
      for (Map.Entry<String, Object> entry : entries) {
        Object key = entry.getKey();
        Object value = entry.getValue();

        if (!key.toString().startsWith("#")) key = map.get(key);
        if (!value.toString().startsWith("#")) value = map.get(value);

        if (key.toString().startsWith("#") & value.toString().startsWith("#")) {
          theme.iconColorsOnSelection.put(key.toString(), value);
        }
      }
    }
  }

  private static String toColorString(@NotNull String key, boolean darkTheme) {
    if (darkTheme && colorPalette.get(key + ".Dark") != null) {
      key += ".Dark";
    }
    String color = colorPalette.get(key);
    return color == null ? key.toLowerCase(Locale.ENGLISH) : color.toLowerCase(Locale.ENGLISH);
  }

  @TestOnly
  public static Map<String, String> getColorPalette() {
    return Collections.unmodifiableMap(colorPalette);
  }

  private static final @NonNls Map<String, String> colorPalette;

  static {
    colorPalette = Map.ofEntries(
      Map.entry("Actions.Red", "#DB5860"),
      Map.entry("Actions.Red.Dark", "#C75450"),
      Map.entry("Actions.Yellow", "#EDA200"),
      Map.entry("Actions.Yellow.Dark", "#F0A732"),
      Map.entry("Actions.Green", "#59A869"),
      Map.entry("Actions.Green.Dark", "#499C54"),
      Map.entry("Actions.Blue", "#389FD6"),
      Map.entry("Actions.Blue.Dark", "#3592C4"),
      Map.entry("Actions.Grey", "#6E6E6E"),
      Map.entry("Actions.Grey.Dark", "#AFB1B3"),
      Map.entry("Actions.GreyInline", "#7F8B91"),
      Map.entry("Actions.GreyInline.Dark", "#7F8B91"),
      Map.entry("Objects.Grey", "#9AA7B0"),
      Map.entry("Objects.Blue", "#40B6E0"),
      Map.entry("Objects.Green", "#62B543"),
      Map.entry("Objects.Yellow", "#F4AF3D"),
      Map.entry("Objects.YellowDark", "#D9A343"),
      Map.entry("Objects.Purple", "#B99BF8"),
      Map.entry("Objects.Pink", "#F98B9E"),
      Map.entry("Objects.Red", "#F26522"),
      Map.entry("Objects.RedStatus", "#E05555"),
      Map.entry("Objects.GreenAndroid", "#3DDC84"),
      Map.entry("Objects.BlackText", "#231F20"),
      Map.entry("Checkbox.Background.Default", "#FFFFFF"),
      Map.entry("Checkbox.Background.Default.Dark", "#43494A"),
      Map.entry("Checkbox.Background.Disabled", "#F2F2F2"),
      Map.entry("Checkbox.Background.Disabled.Dark", "#3C3F41"),
      Map.entry("Checkbox.Border.Default", "#b0b0b0"),
      Map.entry("Checkbox.Border.Default.Dark", "#6B6B6B"),
      Map.entry("Checkbox.Border.Disabled", "#BDBDBD"),
      Map.entry("Checkbox.Border.Disabled.Dark", "#545556"),
      Map.entry("Checkbox.Focus.Thin.Default", "#7B9FC7"),
      Map.entry("Checkbox.Focus.Thin.Default.Dark", "#466D94"),
      Map.entry("Checkbox.Focus.Wide", "#97C3F3"),
      Map.entry("Checkbox.Focus.Wide.Dark", "#3D6185"),
      Map.entry("Checkbox.Foreground.Disabled", "#ABABAB"),
      Map.entry("Checkbox.Foreground.Disabled.Dark", "#606060"),
      Map.entry("Checkbox.Background.Selected", "#4F9EE3"),
      Map.entry("Checkbox.Background.Selected.Dark", "#43494A"),
      Map.entry("Checkbox.Border.Selected", "#4B97D9"),
      Map.entry("Checkbox.Border.Selected.Dark", "#6B6B6B"),
      Map.entry("Checkbox.Foreground.Selected", "#FEFEFE"),
      Map.entry("Checkbox.Foreground.Selected.Dark", "#A7A7A7"),
      Map.entry("Checkbox.Focus.Thin.Selected", "#ACCFF7"),
      Map.entry("Checkbox.Focus.Thin.Selected.Dark", "#466D94"),
      Map.entry("Tree.iconColor", "#808080"),
      Map.entry("Tree.iconColor.Dark", "#AFB1B3")
    );
  }

  public @NonNls String getId() {
    return id;
  }

  public @Nullable String getEditorScheme() {
    return editorScheme;
  }

  public String @Nullable [] getAdditionalEditorSchemes() {
    return additionalEditorSchemes;
  }

  public Map<String, Object> getBackground() {
    return background;
  }

  public Map<String, Object> getEmptyFrameBackground() {
    return emptyFrameBackground;
  }

  public void applyProperties(@NotNull UIDefaults defaults) {
    if (ui == null) {
      return;
    }

    loadColorPalette(defaults);

    for (Map.Entry<String, Object> entry : ui.entrySet()) {
      apply(this, entry.getKey(), entry.getValue(), defaults);
    }
  }

  private void loadColorPalette(@NotNull UIDefaults defaults) {
    if (colors != null) {
      for (Map.Entry<String, Object> entry : colors.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof String) {
          Color color = parseColor((String)value);
          if (color != null) {
            defaults.put("ColorPalette." + entry.getKey(), color);
          }
        }
      }
    }
  }

  @ApiStatus.Internal
  public @Nullable IconPathPatcher getPatcher() {
    return patcher;
  }

  @ApiStatus.Internal
  public void setPatcher(@Nullable IconPathPatcher patcher) {
    this.patcher = patcher;
  }

  public SVGLoader.SvgElementColorPatcherProvider getColorPatcher() {
    return colorPatcher;
  }

  public SVGLoader.SvgElementColorPatcherProvider getSelectionColorPatcher() {
    return selectionColorPatcher;
  }

  @ApiStatus.Internal
  public @NotNull ClassLoader getProviderClassLoader() {
    if (providerClassLoader == null) {
      throw new RuntimeException("The theme classloader has already been detached");
    }

    return providerClassLoader;
  }

  @ApiStatus.Internal
  public void setProviderClassLoader(@Nullable ClassLoader providerClassLoader) {
    this.providerClassLoader = providerClassLoader;
  }

  private static void apply(@NotNull UITheme theme, String key, Object value, UIDefaults defaults) {
    String valueStr = value.toString();
    Color color = null;
    if (theme.colors != null) {
      Object obj = theme.colors.get(valueStr);
      if (obj != null) {
        color = parseColor(obj.toString());
        if (color != null && !key.startsWith("*")) {
          defaults.put(key, color);
          return;
        }
      }
    }
    value = color == null ? parseValue(key, valueStr) : color;
    if (key.startsWith("*.")) {
      String tail = key.substring(1);
      addPattern(key, value, defaults);

      for (Object k : defaults.keySet().toArray()) {
        if (k instanceof String && ((String)k).endsWith(tail)) {
          defaults.put(k, value);
        }
      }
    }
    else {
      defaults.put(key, value);
    }
  }

  private static @NotNull String createUIKey(String key, String propertyName) {
    if ("UI".equals(propertyName)) {
      return key + propertyName;
    }
    else {
      return key + "." + propertyName;
    }
  }

  private static @Nullable Object getOSCustomization(@NotNull Map<String, Object> map) {
    String osKey = SystemInfoRt.isWindows ? OS_WINDOWS_KEY :
                   SystemInfoRt.isMac ? OS_MACOS_KEY :
                   SystemInfoRt.isLinux ? OS_LINUX_KEY : null;
    if (osKey != null && map.containsKey(osKey)) {
      return map.get(osKey);
    }
    else {
      return map.get(OS_DEFAULT_KEY);
    }
  }

  private static boolean isOSCustomization(Map<String, Object> map) {
    return map.containsKey(OS_MACOS_KEY) ||
           map.containsKey(OS_WINDOWS_KEY) ||
           map.containsKey(OS_LINUX_KEY) ||
           map.containsKey(OS_DEFAULT_KEY);
  }

  @SuppressWarnings("unchecked")
  private static void addPattern(String key, Object value, UIDefaults defaults) {
    Object o = defaults.get("*");
    if (!(o instanceof Map)) {
      o = new HashMap<String, Object>();
      defaults.put("*", o);
    }
    @SuppressWarnings("rawtypes")
    Map map = (Map<?, ?>)o;
    if (key != null && key.startsWith("*.")) {
      map.put(key.substring(2), value);
    }
  }

  public static Object parseValue(String key, @NotNull String value, @NotNull ClassLoader classLoader) {
    try {
      switch (value) {
        case "null" -> {
          return null;
        }
        case "true" -> {
          return Boolean.TRUE;
        }
        case "false" -> {
          return Boolean.FALSE;
        }
      }

      if (value.endsWith(".png") || value.endsWith(".svg")) {
        Icon icon = ImageDataByPathLoader.Companion.findIconByPath(value, classLoader, null, null);
        if (icon != null) {
          return icon;
        }
      }

      if (key.endsWith("Insets") || key.endsWith(".insets") || key.endsWith("padding")) {
        return parseInsets(value);
      }
      else if (key.endsWith("Border") || key.endsWith("border")) {
        try {
          String[] ints = value.split(",");
          if (ints.length == 4) {
            return new BorderUIResource.EmptyBorderUIResource(parseInsets(value));
          }
          else if (ints.length == 5) {
            return JBUI.asUIResource(JBUI.Borders.customLine(ColorHexUtil.fromHex(ints[4]),
                                                             Integer.parseInt(ints[0]),
                                                             Integer.parseInt(ints[1]),
                                                             Integer.parseInt(ints[2]),
                                                             Integer.parseInt(ints[3])));
          }
          Color color = ColorHexUtil.fromHexOrNull(value);
          if (color == null) {
            Class<?> aClass = classLoader.loadClass(value);
            Constructor<?> constructor = aClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
          }
          else {
            return JBUI.asUIResource(JBUI.Borders.customLine(color, 1));
          }
        }
        catch (Exception e) {
          LOG.warn(e);
        }
      }
      else if (key.endsWith("Size")) {
        return parseSize(value);
      }
      else if (key.endsWith("Width") || key.endsWith("Height")) {
        return getIntegerOrFloat(value, key);
      }
      else if (key.endsWith("grayFilter")) {
        return parseGrayFilter(value);
      }
      else if (value.startsWith("AllIcons.")) {
        return ImageDataByPathLoaderKt.getReflectiveIcon(value, UITheme.class.getClassLoader());
      }
      else if (!value.startsWith("#") && getIntegerOrFloat(value, null) != null) {
        return getIntegerOrFloat(value, key);
      }
      else {
        Color color = parseColor(value);
        if (color != null) {
          return new ColorUIResource(color);
        }
        Integer intVal = getInteger(value, null);
        if (intVal != null) {
          return intVal;
        }
      }
    }
    catch (Exception e) {
      LOG.warn("Can't parse '" + value + "' for key '" + key + "'");
    }

    return value;
  }

  public static Object parseValue(String key, @NotNull String value) {
    ClassLoader classLoader = UIManager.getLookAndFeel().getClass().getClassLoader();
    return parseValue(key, value, classLoader);
  }

  private static Insets parseInsets(@NotNull String value) {
    String[] numbers = value.split(",");
    return new JBInsets(Integer.parseInt(numbers[0]),
                        Integer.parseInt(numbers[1]),
                        Integer.parseInt(numbers[2]),
                        Integer.parseInt(numbers[3]))
      .asUIResource();
  }

  private static UIUtil.GrayFilter parseGrayFilter(String value) {
    String[] numbers = value.split(",");
    return new UIUtil.GrayFilter(Integer.parseInt(numbers[0]),
                                 Integer.parseInt(numbers[1]),
                                 Integer.parseInt(numbers[2]))
      .asUIResource();
  }

  @SuppressWarnings("UseJBColor")
  private static @Nullable Color parseColor(String value) {
    if (value != null) {
      //noinspection SSBasedInspection
      if (value.startsWith("#")) {
        value = value.substring(1);
      }
      if (value.length() == 8) {
        Color color = ColorHexUtil.fromHex(value.substring(0, 6));
        try {
          int alpha = Integer.parseInt(value.substring(6, 8), 16);
          return new ColorUIResource(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        }
        catch (Exception ignore) {
        }
        return null;
      }
    }
    Color color = ColorHexUtil.fromHex(value, null);
    return color == null ? null : new ColorUIResource(color);
  }

  private static Integer getInteger(String value, @Nullable String key) {
    try {
      //noinspection SSBasedInspection
      if (value.endsWith(".0")) {
        value = value.substring(0, value.length() - ".0".length());
      }
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      if (key != null) {
        LOG.warn("Can't parse: " + key + " = " + value);
      }
      return null;
    }
  }

  private static Number getIntegerOrFloat(String value, @Nullable String key) {
    if (value.contains(".")) {
      try {
        return Float.parseFloat(value);
      }
      catch (NumberFormatException e) {
        if (key != null) {
          LOG.warn("Can't parse: " + key + " = " + value);
        }
        return null;
      }
    }
    return getInteger(value, key);
  }

  private static Dimension parseSize(@NotNull String value) {
    String[] numbers = value.split(",");
    return new JBDimension(Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1])).asUIResource();
  }

  public String getEditorSchemeName() {
    return editorSchemeName;
  }

  public void setEditorSchemeName(String editorSchemeName) {
    this.editorSchemeName = editorSchemeName;
  }

  static final class PaletteScope {
    final Map<String, String> newPalette = new HashMap<>();
    final Map<String, Integer> alphas = new HashMap<>();

    private long[] hash;

    long @NotNull [] digest() {
      if (hash != null) {
        return hash;
      }

      // order is significant - use TreeMap
      hash = new InsecureHashBuilder()
        .stringMap(newPalette)
        .stringIntMap(alphas)
        .build();
      return hash;
    }
  }

  static final class PaletteScopeManager {
    final PaletteScope ui = new PaletteScope();
    final PaletteScope checkBoxes = new PaletteScope();
    final PaletteScope radioButtons = new PaletteScope();
    final PaletteScope trees = new PaletteScope();

    PaletteScope getScope(String colorKey) {
      if (colorKey.startsWith("Checkbox.")) return checkBoxes;
      if (colorKey.startsWith("Radio.")) return radioButtons;
      if (colorKey.startsWith("Tree.iconColor")) return trees;
      if (colorKey.startsWith("Objects.")) return ui;
      if (colorKey.startsWith("Actions.")) return ui;
      if (colorKey.startsWith("#")) return ui;

      LOG.warn("No color scope defined for key: " + colorKey);
      return null;
    }

    @Nullable PaletteScope getScopeByPath(@Nullable String path) {
      if (path != null && (path.contains("com/intellij/ide/ui/laf/icons/") || path.contains("/com/intellij/ide/ui/laf/icons/"))) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        if (file.equals("treeCollapsed.svg") || file.equals("treeExpanded.svg")) return trees;
        if (file.startsWith("check")) return checkBoxes;
        if (file.startsWith("radio")) return checkBoxes; //same set of colors as for checkboxes
        return null;
      }
      return ui;
    }
  }

  //<editor-fold desc="JSON deserialization methods">
  @SuppressWarnings("unused")
  private void setName(String name) {
    this.name = name;
  }

  @SuppressWarnings("unused")
  private void setDark(boolean dark) {
    this.dark = dark;
  }

  @SuppressWarnings("unused")
  private void setAuthor(String author) {
    this.author = author;
  }

  @SuppressWarnings("unused")
  private void setUi(Map<String, Object> ui) {
    this.ui = ui;
  }

  @SuppressWarnings("unused")
  private void setIcons(@Nullable Map<String, Object> icons) {
    this.icons = icons;
  }

  @SuppressWarnings("unused")
  private void setParentTheme(@Nullable String parentTheme) {
    this.parentTheme = parentTheme;
  }

  @SuppressWarnings("unused")
  public void setEditorScheme(String editorScheme) {
    this.editorScheme = editorScheme;
  }

  @SuppressWarnings("unused")
  public void setAdditionalEditorSchemes(String[] additionalEditorSchemes) {
    this.additionalEditorSchemes = additionalEditorSchemes;
  }

  public void setBackground(Map<String, Object> background) {
    this.background = background;
  }

  public void setIconColorsOnSelection(@Nullable Map<String, Object> iconColorsOnSelection) {
    this.iconColorsOnSelection = iconColorsOnSelection;
  }

  public void setEmptyFrameBackground(Map<String, Object> emptyFrameBackground) {
    this.emptyFrameBackground = emptyFrameBackground;
  }

  public @Nullable Map<String, Object> getColors() {
    return colors;
  }

  public void setColors(@Nullable Map<String, Object> colors) {
    this.colors = colors;
  }
  //</editor-fold>
}