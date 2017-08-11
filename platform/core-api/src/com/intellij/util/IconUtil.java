/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.ide.FileIconPatcher;
import com.intellij.ide.FileIconProvider;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.ui.IconDeferrer;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.ui.*;
import com.intellij.util.ui.JBUI.JBUIScaleUpdatable;
import com.intellij.util.ui.JBUI.ScaleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;


/**
 * @author max
 * @author Konstantin Bulenkov
 */
public class IconUtil {
  private static final Key<Boolean> PROJECT_WAS_EVER_INITIALIZED = Key.create("iconDeferrer:projectWasEverInitialized");

  private static boolean wasEverInitialized(@NotNull Project project) {
    Boolean was = project.getUserData(PROJECT_WAS_EVER_INITIALIZED);
    if (was == null) {
      if (project.isInitialized()) {
        was = true;
        project.putUserData(PROJECT_WAS_EVER_INITIALIZED, true);
      }
      else {
        was = false;
      }
    }

    return was.booleanValue();
  }

  @NotNull
  public static Icon cropIcon(@NotNull Icon icon, int maxWidth, int maxHeight) {
    if (icon.getIconHeight() <= maxHeight && icon.getIconWidth() <= maxWidth) {
      return icon;
    }

    final int w = Math.min(icon.getIconWidth(), maxWidth);
    final int h = Math.min(icon.getIconHeight(), maxHeight);

    final BufferedImage image = GraphicsEnvironment
      .getLocalGraphicsEnvironment()
      .getDefaultScreenDevice()
      .getDefaultConfiguration()
      .createCompatibleImage(icon.getIconWidth(), icon.getIconHeight(), Transparency.TRANSLUCENT);
    final Graphics2D g = image.createGraphics();
    icon.paintIcon(new JPanel(), g, 0, 0);
    g.dispose();

    final BufferedImage img = UIUtil.createImage(g, w, h, Transparency.TRANSLUCENT);
    final int offX = icon.getIconWidth() > maxWidth ? (icon.getIconWidth() - maxWidth) / 2 : 0;
    final int offY = icon.getIconHeight() > maxHeight ? (icon.getIconHeight() - maxHeight) / 2 : 0;
    for (int col = 0; col < w; col++) {
      for (int row = 0; row < h; row++) {
        img.setRGB(col, row, image.getRGB(col + offX, row + offY));
      }
    }

    return new ImageIcon(img);
  }

  @NotNull
  public static Icon cropIcon(@NotNull Icon icon, @NotNull Rectangle area) {
    if (!new Rectangle(icon.getIconWidth(), icon.getIconHeight()).contains(area)) {
      return icon;
    }
    return new CropIcon(icon, area);
  }

  @NotNull
  public static Icon flip(@NotNull final Icon icon, final boolean horizontal) {
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D)g.create();
        try {
          AffineTransform transform =
            AffineTransform.getTranslateInstance(horizontal ? x + getIconWidth() : x, horizontal ? y : y + getIconHeight());
          transform.concatenate(AffineTransform.getScaleInstance(horizontal ? -1 : 1, horizontal ? 1 : -1));
          transform.preConcatenate(g2d.getTransform());
          g2d.setTransform(transform);
          icon.paintIcon(c, g2d, 0, 0);
        }
        finally {
          g2d.dispose();
        }
      }

      @Override
      public int getIconWidth() {
        return icon.getIconWidth();
      }

      @Override
      public int getIconHeight() {
        return icon.getIconHeight();
      }
    };
  }

  private static final NullableFunction<FileIconKey, Icon> ICON_NULLABLE_FUNCTION = key -> {
    final VirtualFile file = key.getFile();
    final int flags = filterFileIconFlags(file, key.getFlags());
    final Project project = key.getProject();

    if (!file.isValid() || project != null && (project.isDisposed() || !wasEverInitialized(project))) return null;

    final Icon providersIcon = getProvidersIcon(file, flags, project);
    Icon icon = providersIcon == null ? VirtualFilePresentation.getIconImpl(file) : providersIcon;

    final boolean dumb = project != null && DumbService.getInstance(project).isDumb();
    for (FileIconPatcher patcher : getPatchers()) {
      if (dumb && !DumbService.isDumbAware(patcher)) {
        continue;
      }

      // render without locked icon patch since we are going to apply it later anyway
      icon = patcher.patchIcon(icon, file, flags & ~Iconable.ICON_FLAG_READ_STATUS, project);
    }

    if (file.is(VFileProperty.SYMLINK)) {
      icon = new LayeredIcon(icon, PlatformIcons.SYMLINK_ICON);
    }
    if (BitUtil.isSet(flags, Iconable.ICON_FLAG_READ_STATUS) &&
        (!file.isWritable() || !WritingAccessProvider.isPotentiallyWritable(file, project))) {
      icon = new LayeredIcon(icon, PlatformIcons.LOCKED_ICON);
    }

    Iconable.LastComputedIcon.put(file, icon, flags);

    return icon;
  };

  @Iconable.IconFlags
  private static int filterFileIconFlags(@NotNull VirtualFile file, @Iconable.IconFlags int flags) {
    UserDataHolder fileTypeDataHolder = ObjectUtils.tryCast(file.getFileType(), UserDataHolder.class);
    int fileTypeFlagIgnoreMask = Iconable.ICON_FLAG_IGNORE_MASK.get(fileTypeDataHolder, 0);
    int flagIgnoreMask = Iconable.ICON_FLAG_IGNORE_MASK.get(file, fileTypeFlagIgnoreMask);
    //noinspection MagicConstant
    return flags & ~flagIgnoreMask;
  }

  public static Icon getIcon(@NotNull final VirtualFile file, @Iconable.IconFlags final int flags, @Nullable final Project project) {
    Icon lastIcon = Iconable.LastComputedIcon.get(file, flags);

    final Icon base = lastIcon != null ? lastIcon : VirtualFilePresentation.getIconImpl(file);
    return IconDeferrer.getInstance().defer(base, new FileIconKey(file, project, flags), ICON_NULLABLE_FUNCTION);
  }

  @Nullable
  private static Icon getProvidersIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, Project project) {
    for (FileIconProvider provider : getProviders()) {
      final Icon icon = provider.getIcon(file, flags, project);
      if (icon != null) return icon;
    }
    return null;
  }

  @NotNull
  public static Icon getEmptyIcon(boolean showVisibility) {
    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(createEmptyIconLike(PlatformIcons.CLASS_ICON_PATH), 0);
    if (showVisibility) {
      baseIcon.setIcon(createEmptyIconLike(PlatformIcons.PUBLIC_ICON_PATH), 1);
    }
    return baseIcon;
  }

  @NotNull
  private static Icon createEmptyIconLike(@NotNull String baseIconPath) {
    Icon baseIcon = IconLoader.findIcon(baseIconPath);
    if (baseIcon == null) {
      return EmptyIcon.ICON_16;
    }
    return EmptyIcon.create(baseIcon);
  }

  private static class FileIconProviderHolder {
    private static final FileIconProvider[] myProviders = Extensions.getExtensions(FileIconProvider.EP_NAME);
  }

  @NotNull
  private static FileIconProvider[] getProviders() {
    return FileIconProviderHolder.myProviders;
  }

  private static class FileIconPatcherHolder {
    private static final FileIconPatcher[] ourPatchers = Extensions.getExtensions(FileIconPatcher.EP_NAME);
  }

  @NotNull
  private static FileIconPatcher[] getPatchers() {
    return FileIconPatcherHolder.ourPatchers;
  }

  public static Image toImage(@NotNull Icon icon) {
    return IconLoader.toImage(icon);
  }

  @NotNull
  public static Icon getAddIcon() {
    return getToolbarDecoratorIcon("add.png");
  }

  @NotNull
  public static Icon getRemoveIcon() {
    return getToolbarDecoratorIcon("remove.png");
  }

  @NotNull
  public static Icon getMoveUpIcon() {
    return getToolbarDecoratorIcon("moveUp.png");
  }

  @NotNull
  public static Icon getMoveDownIcon() {
    return getToolbarDecoratorIcon("moveDown.png");
  }

  @NotNull
  public static Icon getEditIcon() {
    return getToolbarDecoratorIcon("edit.png");
  }

  @NotNull
  public static Icon getAddClassIcon() {
    return getToolbarDecoratorIcon("addClass.png");
  }

  @NotNull
  public static Icon getAddPatternIcon() {
    return getToolbarDecoratorIcon("addPattern.png");
  }

  @NotNull
  public static Icon getAddJiraPatternIcon() {
    return getToolbarDecoratorIcon("addJira.png");
  }

  @NotNull
  public static Icon getAddYouTrackPatternIcon() {
    return getToolbarDecoratorIcon("addYouTrack.png");
  }

  @NotNull
  public static Icon getAddBlankLineIcon() {
    return getToolbarDecoratorIcon("addBlankLine.png");
  }

  @NotNull
  public static Icon getAddPackageIcon() {
    return getToolbarDecoratorIcon("addPackage.png");
  }

  @NotNull
  public static Icon getAddLinkIcon() {
    return getToolbarDecoratorIcon("addLink.png");
  }

  @NotNull
  public static Icon getAddFolderIcon() {
    return getToolbarDecoratorIcon("addFolder.png");
  }

  @NotNull
  public static Icon getAnalyzeIcon() {
    return getToolbarDecoratorIcon("analyze.png");
  }

  public static void paintInCenterOf(@NotNull Component c, @NotNull Graphics g, @NotNull Icon icon) {
    final int x = (c.getWidth() - icon.getIconWidth()) / 2;
    final int y = (c.getHeight() - icon.getIconHeight()) / 2;
    icon.paintIcon(c, g, x, y);
  }

  @NotNull
  private static Icon getToolbarDecoratorIcon(@NotNull String name) {
    return IconLoader.getIcon(getToolbarDecoratorIconsFolder() + name);
  }

  @NotNull
  private static String getToolbarDecoratorIconsFolder() {
    return "/toolbarDecorator/" + (SystemInfo.isMac ? "mac/" : "");
  }

  /**
   * Result icons look like original but have equal (maximum) size
   */
  @NotNull
  public static Icon[] getEqualSizedIcons(@NotNull Icon... icons) {
    Icon[] result = new Icon[icons.length];
    int width = 0;
    int height = 0;
    for (Icon icon : icons) {
      width = Math.max(width, icon.getIconWidth());
      height = Math.max(height, icon.getIconHeight());
    }
    for (int i = 0; i < icons.length; i++) {
      result[i] = new IconSizeWrapper(icons[i], width, height);
    }
    return result;
  }

  @NotNull
  public static Icon toSize(@Nullable Icon icon, int width, int height) {
    return new IconSizeWrapper(icon, width, height);
  }

  public static class IconSizeWrapper implements Icon {
    private final Icon myIcon;
    private final int myWidth;
    private final int myHeight;

    protected IconSizeWrapper(@Nullable Icon icon, int width, int height) {
      myIcon = icon;
      myWidth = width;
      myHeight = height;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      paintIcon(myIcon, c, g, x, y);
    }

    protected void paintIcon(@Nullable Icon icon, Component c, Graphics g, int x, int y) {
      if (icon == null) return;
      x += (myWidth - icon.getIconWidth()) / 2;
      y += (myHeight - icon.getIconHeight()) / 2;
      icon.paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
      return myWidth;
    }

    @Override
    public int getIconHeight() {
      return myHeight;
    }
  }

  private static class CropIcon implements Icon {
    private final Icon mySrc;
    private final Rectangle myCrop;

    private CropIcon(@NotNull Icon src, @NotNull Rectangle crop) {
      mySrc = src;
      myCrop = crop;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      mySrc.paintIcon(c, g, x - myCrop.x, y - myCrop.y);
    }

    @Override
    public int getIconWidth() {
      return myCrop.width;
    }

    @Override
    public int getIconHeight() {
      return myCrop.height;
    }
  }

  @NotNull
  public static Icon scale(@NotNull final Icon source, double _scale) {
    final int hiDPIscale;
    if (source instanceof ImageIcon) {
      Image image = ((ImageIcon)source).getImage();
      hiDPIscale = RetinaImage.isAppleHiDPIScaledImage(image) || image instanceof JBHiDPIScaledImage ? 2 : 1;
    }
    else {
      hiDPIscale = 1;
    }
    final double scale = Math.min(32, Math.max(.1, _scale));
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D)g.create();
        try {
          g2d.translate(x, y);
          AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
          transform.preConcatenate(g2d.getTransform());
          g2d.setTransform(transform);
          g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          source.paintIcon(c, g2d, 0, 0);
        }
        finally {
          g2d.dispose();
        }
      }

      @Override
      public int getIconWidth() {
        return (int)(source.getIconWidth() * scale) / hiDPIscale;
      }

      @Override
      public int getIconHeight() {
        return (int)(source.getIconHeight() * scale) / hiDPIscale;
      }
    };
  }

  /**
   * Returns a scaled icon instance.
   * <p>
   * The method delegates to {@link ScalableIcon#scale(float)} when applicable,
   * otherwise defaults to {@link #scale(Icon, double)}
   * <p>
   * In the following example:
   * <pre>
   * Icon myIcon = new MyIcon();
   * Icon scaledIcon = IconUtil.scale(myIcon, myComp, 2f);
   * Icon anotherScaledIcon = IconUtil.scale(scaledIcon, myComp, 2f);
   * assert(scaledIcon.getIconWidth() == anotherScaledIcon.getIconWidth()); // compare the scale of the icons
   * </pre>
   * The result of the assertion depends on {@code MyIcon} implementation. When {@code scaledIcon} is an instance of {@link ScalableIcon},
   * then {@code anotherScaledIcon} should be scaled according to the {@link ScalableIcon} javadoc, and the assertion should pass.
   * Otherwise, {@code anotherScaledIcon} should be 2 times bigger than {@code scaledIcon}, and 4 times bigger than {@code myIcon}.
   * So, prior to scale the icon recursively, the returned icon should be inspected for its type to understand the result.
   * But recursive scale should better be avoided.
   *
   * @param icon the icon to scale
   * @param ancestor the component (or its ancestor) painting the icon, or null when not available
   * @param scale the scale factor
   * @return the scaled icon
   */
  @NotNull
  public static Icon scale(@NotNull Icon icon, @Nullable Component ancestor, float scale) {
    if (icon instanceof ScalableIcon) {
      if (icon instanceof JBUI.JBUIScaleUpdatable) {
        ((JBUI.JBUIScaleUpdatable)icon).updateJBUIScale(ancestor != null ? ancestor.getGraphicsConfiguration() : null);
      }
      return ((ScalableIcon)icon).scale(scale);
    }
    return scale(icon, scale);
  }

  /**
   * Returns a scaled icon instance, in scale of the provided font size.
   * <p>
   * The method delegates to {@link ScalableIcon#scale(float)} when applicable,
   * otherwise defaults to {@link #scale(Icon, double)}
   * <p>
   * Refer to {@link #scale(Icon, Component, float)} for more details.
   *
   * @param icon the icon to scale
   * @param ancestor the component (or its ancestor) painting the icon, or null when not available
   * @param fontSize the reference font size
   * @return the scaled icon
   */
  @NotNull
  public static Icon scaleByFont(@NotNull Icon icon, @Nullable Component ancestor, float fontSize) {
    float scale = JBUI.getFontScale(fontSize);
    if (icon instanceof ScalableIcon) {
      if (icon instanceof JBUIScaleUpdatable) {
        JBUI.JBUIScaleUpdatable jbuiIcon = (JBUI.JBUIScaleUpdatable)icon;
        jbuiIcon.updateJBUIScale(ancestor != null ? ancestor.getGraphicsConfiguration() : null);
        // take into account the user scale of the icon
        float usrScale = jbuiIcon.getJBUIScale(ScaleType.USR);
        scale /= usrScale;
      }
      return ((ScalableIcon)icon).scale(scale);
    }
    return scale(icon, scale);
  }

  @NotNull
  public static Icon colorize(@NotNull Icon source, @NotNull Color color) {
    return colorize(source, color, false);
  }

  @NotNull
  public static Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color) {
    return colorize(g, source, color, false);
  }

  @NotNull
  public static Icon colorize(@NotNull Icon source, @NotNull Color color, boolean keepGray) {
    return filterIcon(null, source, new ColorFilter(color, keepGray));
  }

  @NotNull
  public static Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color, boolean keepGray) {
    return filterIcon(g, source, new ColorFilter(color, keepGray));
  }
  @NotNull
  public static Icon desaturate(@NotNull Icon source) {
    return filterIcon(null, source, new DesaturationFilter());
  }

  @NotNull
  public static Icon brighter(@NotNull Icon source, int tones) {
    return filterIcon(null, source, new BrighterFilter(tones));
  }

  @NotNull
  public static Icon darker(@NotNull Icon source, int tones) {
    return filterIcon(null, source, new DarkerFilter(tones));
  }

  @NotNull
  private static Icon filterIcon(Graphics2D g, @NotNull Icon source, @NotNull Filter filter) {
    BufferedImage src = g != null ? UIUtil.createImage(g, source.getIconWidth(), source.getIconHeight(), Transparency.TRANSLUCENT) :
                                    UIUtil.createImage(source.getIconWidth(), source.getIconHeight(), Transparency.TRANSLUCENT);
    Graphics2D g2d = src.createGraphics();
    source.paintIcon(null, g2d, 0, 0);
    g2d.dispose();
    BufferedImage img = g != null ? UIUtil.createImage(g, source.getIconWidth(), source.getIconHeight(), Transparency.TRANSLUCENT) :
                                    UIUtil.createImage(source.getIconWidth(), source.getIconHeight(), Transparency.TRANSLUCENT);
    int[] rgba = new int[4];
    for (int y = 0; y < src.getRaster().getHeight(); y++) {
      for (int x = 0; x < src.getRaster().getWidth(); x++) {
        src.getRaster().getPixel(x, y, rgba);
        if (rgba[3] != 0) {
          img.getRaster().setPixel(x, y, filter.convert(rgba));
        }
      }
    }
    return createImageIcon(img);
  }
  
  private static abstract class Filter {
    @NotNull
    abstract int[] convert(@NotNull int[] rgba);
  }

  private static class ColorFilter extends Filter {
    private final float[] myBase;
    private final boolean myKeepGray;

    private ColorFilter(@NotNull Color color, boolean keepGray) {
      myKeepGray = keepGray;
      myBase = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    }

    @NotNull
    @Override
    int[] convert(@NotNull int[] rgba) {
      float[] hsb = new float[3];
      Color.RGBtoHSB(rgba[0], rgba[1], rgba[2], hsb);
      int rgb = Color.HSBtoRGB(myBase[0], myBase[1] * (myKeepGray ? hsb[1] : 1f), myBase[2] * hsb[2]);
      return new int[]{rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff, rgba[3]};
    }
  }
  
  private static class DesaturationFilter extends Filter {
    @NotNull
    @Override
    int[] convert(@NotNull int[] rgba) {
      int min = Math.min(Math.min(rgba[0], rgba[1]), rgba[2]);
      int max = Math.max(Math.max(rgba[0], rgba[1]), rgba[2]);
      int grey = (max + min) / 2;
      return new int[]{grey, grey, grey, rgba[3]};
    }
  }

  private static class BrighterFilter extends Filter {
    private final int myTones;

    public BrighterFilter(int tones) {
      myTones = tones;
    }

    @NotNull
    @Override
    int[] convert(@NotNull int[] rgba) {
      final float[] hsb = Color.RGBtoHSB(rgba[0], rgba[1], rgba[2], null);
      float brightness = hsb[2];
      for (int i = 0; i < myTones; i++) {
        brightness = Math.min(1, brightness * 1.1F);
        if (brightness == 1) break;
      }
      Color color = Color.getHSBColor(hsb[0], hsb[1], brightness);
      return new int[]{color.getRed(), color.getGreen(), color.getBlue(), rgba[3]};
    }
  }

  private static class DarkerFilter extends Filter {
    private final int myTones;

    public DarkerFilter(int tones) {
      myTones = tones;
    }

    @NotNull
    @Override
    int[] convert(@NotNull int[] rgba) {
      final float[] hsb = Color.RGBtoHSB(rgba[0], rgba[1], rgba[2], null);
      float brightness = hsb[2];
      for (int i = 0; i < myTones; i++) {
        brightness = Math.max(0, brightness / 1.1F);
        if (brightness == 0) break;
      }
      Color color = Color.getHSBColor(hsb[0], hsb[1], brightness);
      return new int[]{color.getRed(), color.getGreen(), color.getBlue(), rgba[3]};
    }
  }

  @NotNull
  public static JBImageIcon createImageIcon(@NotNull final BufferedImage img) {
    return new JBImageIcon(img) {
      @Override
      public int getIconWidth() {
        Image img = getImage();
        return (img instanceof JBHiDPIScaledImage) ?
               ((JBHiDPIScaledImage)img).getUserWidth(null):
               super.getIconWidth();
      }

      @Override
      public int getIconHeight() {
        Image img = getImage();
        return (img instanceof JBHiDPIScaledImage) ?
               ((JBHiDPIScaledImage)img).getUserHeight(null):
               super.getIconHeight();
      }
    };
  }

  @NotNull
  public static Icon textToIcon(@NotNull final String text, @NotNull final Component component, final float fontSize) {
    final Font font = JBFont.create(JBUI.Fonts.label().deriveFont(fontSize));
    FontMetrics metrics = component.getFontMetrics(font);
    final int width = metrics.stringWidth(text) + JBUI.scale(4);
    final int height = metrics.getHeight();

    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g = g.create();
        try {
          GraphicsUtil.setupAntialiasing(g);
          g.setFont(font);
          UIUtil.drawStringWithHighlighting(g, text, x + JBUI.scale(2), y + height - JBUI.scale(1), JBColor.foreground(), JBColor.background());
        }
        finally {
          g.dispose();
        }
      }

      @Override
      public int getIconWidth() {
        return width;
      }

      @Override
      public int getIconHeight() {
        return height;
      }
    };
  }

  @NotNull
  public static Icon addText(@NotNull Icon base, @NotNull String text) {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(base, 0);
    icon.setIcon(textToIcon(text, new JLabel(), JBUI.scale(6f)), 1, SwingConstants.SOUTH_EAST);
    return icon;
  }
}
