// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.fasterxml.jackson.jr.ob.JSON;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.ColorHexUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SVGLoader;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.IconUIResource;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private Map<String, Object> ui;
  private Map<String, Object> icons;
  private IconPathPatcher patcher;
  private Map<String, Object> background;
  private Map<String, Object> emptyFrameBackground;
  private Map<String, Object> colors;
  private ClassLoader providerClassLoader = getClass().getClassLoader();
  private String editorSchemeName;
  private SVGLoader.SvgElementColorPatcherProvider colorPatcher;

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

  public URL getResource(String path) {
    if (isTempTheme()) {
      File file = new File(path);
      if (file.exists()) {
        try {
          return file.toURI().toURL();
        }
        catch (MalformedURLException e) {
          LOG.warn(e);
        }
      }
    }
    return providerClassLoader.getResource(path);
  }

  public @Nullable InputStream getResourceAsStream(String path) {
    if (isTempTheme()) {
      Path file = Path.of(path);
      if (Files.exists(file)) {
        try {
          return Files.newInputStream(file);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return providerClassLoader.getResourceAsStream(path);
  }

  private boolean isTempTheme() {
    return "Temp theme".equals(id);
  }

  // caches classes - must be not extracted to util class
  private static final NotNullLazyValue<JSON> JSON_READER = NotNullLazyValue.atomicLazy(() -> {
    // .disable(JSON.Feature.PRESERVE_FIELD_ORDERING) - cannot be disabled, for unknown reason order is important
    // for example, button label font color for light theme is not white, but black
    return JSON.builder()
      .enable(JSON.Feature.READ_ONLY)
      .build();
  });

  public static @NotNull UITheme loadFromJson(@NotNull InputStream stream,
                                              @NotNull @NonNls String themeId,
                                              @Nullable ClassLoader provider,
                                              @NotNull Function<? super String, String> iconsMapper)
    throws IllegalStateException, IOException {

    //UITheme theme = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), UITheme.class);
    UITheme theme = JSON_READER.getValue().beanFrom(UITheme.class, stream);
    theme.id = themeId;

    if (provider != null) {
      theme.providerClassLoader = provider;
    }

    if (theme.icons == null || theme.icons.isEmpty()) {
      return theme;
    }

    initializeNamedColors(theme);

    theme.patcher = new IconPathPatcher() {
      @Nullable
      @Override
      public String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
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
    if (!(palette instanceof Map)) {
      return theme;
    }

    @SuppressWarnings("rawtypes")
    Map colors = (Map)palette;
    PaletteScopeManager paletteScopeManager = new PaletteScopeManager();
    for (Object o : colors.keySet()) {
      String colorKey = o.toString();
      PaletteScope scope = paletteScopeManager.getScope(colorKey);
      if (scope == null) {
        continue;
      }

      String key = toColorString(colorKey, theme.isDark());
      Object v = colors.get(colorKey);
      if (v instanceof String) {
        String value = (String)v;
        String alpha = null;
        if (value.length() == 9) {
          alpha = value.substring(7);
          value = value.substring(0, 7);
        }
        if (ColorUtil.fromHex(key, null) != null && ColorUtil.fromHex(value, null) != null) {
          scope.newPalette.put(key, value);
          int fillTransparency = -1;
          if (alpha != null) {
            try {
              fillTransparency = Integer.parseInt(alpha, 16);
            }
            catch (Exception ignore) { }
          }
          if (fillTransparency != -1) {
            scope.alphas.put(value, fillTransparency);
          }
        }
      }
    }

    theme.colorPatcher = new SVGLoader.SvgElementColorPatcherProvider() {
      @Override
      public @Nullable SVGLoader.SvgElementColorPatcher forPath(@Nullable String path) {
        PaletteScope scope = paletteScopeManager.getScopeByPath(path);
        if (scope == null) {
          return null;
        }

        byte[] digest = scope.digest();
        Map<String, String> newPalette = scope.newPalette;
        Map<String, Integer> alphas = scope.alphas;
        return SVGLoader.newPatcher(digest, newPalette, alphas);
      }
    };

    return theme;
  }

  private static void initializeNamedColors(UITheme theme) {
    Map<String, Object> map = theme.colors;
    if (map == null) return;

    Set<String> namedColors = map.keySet();
    for (String key : namedColors) {
      Object value = map.get(key);
      if (value instanceof String && !((String)value).startsWith("#")) {
        map.put(key, ObjectUtils.notNull(map.get(map.get(key)), Gray.TRANSPARENT));
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
      Map.entry("Checkbox.Foreground.Selected", "#FFFFFF"),
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

  @Nullable
  public String getEditorScheme() {
    return editorScheme;
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

  public IconPathPatcher getPatcher() {
    return patcher;
  }

  public SVGLoader.SvgElementColorPatcherProvider getColorPatcher() {
    return colorPatcher;
  }

  public @NotNull ClassLoader getProviderClassLoader() {
    return providerClassLoader;
  }

  private static void apply(@NotNull UITheme theme, String key, Object value, UIDefaults defaults) {
    if (value instanceof Map) {
      @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>)value;
      if (isOSCustomization(map)) {
        applyOSCustomizations(theme, map, key, defaults);
      } else {
        for (Map.Entry<String, Object> o : map.entrySet()) {
          apply(theme, createUIKey(key, o.getKey()), o.getValue(), defaults);
        }
      }
    }
    else {
      String valueStr = value.toString();
      Color color = null;
      if (theme.colors != null && theme.colors.containsKey(valueStr)) {
        color = parseColor(String.valueOf(theme.colors.get(valueStr)));
        if (color != null && !key.startsWith("*")) {
          defaults.put(key, color);
          return;
        }
      }
      value = color == null ? parseValue(key, valueStr) : color;
      if (key.startsWith("*.")) {
        String tail = key.substring(1);
        addPattern(key, value, defaults);

        for (Object k : new ArrayList<>(defaults.keySet())) {
          if (k instanceof String && ((String)k).endsWith(tail)) {
            defaults.put(k, value);
          }
        }
      }
      else {
        defaults.put(key, value);
      }
    }
  }

  @NotNull
  private static String createUIKey(String key, String propertyName) {
    if ("UI".equals(propertyName)) {
      return key + propertyName;
    } else {
      return key + "." + propertyName;
    }
  }

  private static void applyOSCustomizations(@NotNull UITheme theme,
                                            Map<String, Object> map,
                                            String key,
                                            UIDefaults defaults) {
    String osKey = SystemInfoRt.isWindows ? OS_WINDOWS_KEY :
                   SystemInfoRt.isMac ? OS_MACOS_KEY :
                   SystemInfoRt.isLinux ? OS_LINUX_KEY : null;
    if (osKey != null && map.containsKey(osKey)) {
      apply(theme, key, map.get(osKey), defaults);
    }
    else if (map.containsKey(OS_DEFAULT_KEY)) {
      apply(theme, key, map.get(OS_DEFAULT_KEY), defaults);
    }
  }

  private static boolean isOSCustomization(Map<String, Object> map) {
    return map.containsKey(OS_MACOS_KEY)
        || map.containsKey(OS_WINDOWS_KEY)
        || map.containsKey(OS_LINUX_KEY)
        || map.containsKey(OS_DEFAULT_KEY);
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
    switch (value) {
      case "null":
        return null;
      case "true":
        return Boolean.TRUE;
      case "false":
        return Boolean.FALSE;
    }

    if (value.endsWith(".png") || value.endsWith(".svg")) {
      Icon icon = IconLoader.findIcon(value, classLoader);
      if (icon != null) {
        return icon;
      }
    }

    if (key.endsWith("Insets") || key.endsWith("padding")) {
      return parseInsets(value);
    }
    else if (key.endsWith("Border") || key.endsWith("border")) {
      try {
        String[] ints = value.split(",");
        if (ints.length == 4) {
          return new BorderUIResource.EmptyBorderUIResource(parseInsets(value));
        }
        else if (ints.length == 5) {
          return JBUI.asUIResource(JBUI.Borders.customLine(ColorUtil.fromHex(ints[4]),
                                                           Integer.parseInt(ints[0]),
                                                           Integer.parseInt(ints[1]),
                                                           Integer.parseInt(ints[2]),
                                                           Integer.parseInt(ints[3])));
        }
        Color color = ColorHexUtil.fromHexOrNull(value);
        if (color != null) {
          return JBUI.asUIResource(JBUI.Borders.customLine(color, 1));
        }
        else {
          Class<?> aClass = classLoader.loadClass(value);
          Constructor<?> constructor = aClass.getDeclaredConstructor();
          constructor.setAccessible(true);
          return constructor.newInstance();
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
      return getInteger(value, key);
    }
    else if (key.endsWith("grayFilter")) {
      return parseGrayFilter(value);
    }
    else {
      Icon icon = value.startsWith("AllIcons.") ? IconLoader.getReflectiveIcon(value, AllIcons.class.getClassLoader()) : null;
      if (icon != null) {
        return new IconUIResource(icon);
      }
      Color color = parseColor(value);
      if (color != null) {
        return new ColorUIResource(color);
      }
      Integer intVal = getInteger(value, null);
      if (intVal != null) {
        return intVal;
      }
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
        Color color = ColorUtil.fromHex(value.substring(0, 6));
        try {
          int alpha = Integer.parseInt(value.substring(6, 8), 16);
          return new ColorUIResource(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        }
        catch (Exception ignore) { }
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
        LOG.warn(key + " = " + value);
      }
      return null;
    }
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

    private byte[] hash = null;

    byte @NotNull [] digest() {
      if (hash != null) return hash;

      final Hasher hasher = Hashing.sha256().newHasher();
      //order is significant
      for (Map.Entry<String, String> e : new TreeMap<>(newPalette).entrySet()) {
        hasher.putString(e.getKey(), StandardCharsets.UTF_8);
        hasher.putString(e.getValue(), StandardCharsets.UTF_8);
      }
      //order is significant
      for (Map.Entry<String, Integer> e : new TreeMap<>(alphas).entrySet()) {
        hasher.putString(e.getKey(), StandardCharsets.UTF_8);
        final Integer value = e.getValue();
        if (value != null) {
          hasher.putInt(value);
        }
      }
      hash = hasher.hash().asBytes();
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
      if (path != null && path.contains("/com/intellij/ide/ui/laf/icons/")) {
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
  private void setIcons(Map<String, Object> icons) {
    this.icons = icons;
  }

  @SuppressWarnings("unused")
  public void setEditorScheme(String editorScheme) {
    this.editorScheme = editorScheme;
  }

  public void setBackground(Map<String, Object> background) {
    this.background = background;
  }

  public void setEmptyFrameBackground(Map<String, Object> emptyFrameBackground) {
    this.emptyFrameBackground = emptyFrameBackground;
  }

  public Map<String, Object> getColors() {
    return colors;
  }

  public void setColors(Map<String, Object> colors) {
    this.colors = colors;
  }
  //</editor-fold>
}