// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconPatcher;
import com.intellij.ide.FileIconProvider;
import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.fileTypes.DirectoryFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.ui.*;
import com.intellij.ui.icons.CompositeIcon;
import com.intellij.ui.icons.CopyableIcon;
import com.intellij.ui.scale.*;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RGBImageFilter;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static com.intellij.ui.scale.ScaleType.OBJ_SCALE;
import static com.intellij.ui.scale.ScaleType.USR_SCALE;

/**
 * @author Konstantin Bulenkov
 */
public final class IconUtil {
  public static final Key<Integer> ICON_FLAG_IGNORE_MASK = new Key<>("ICON_FLAG_IGNORE_MASK");
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

  public static @NotNull Icon cropIcon(@NotNull Icon icon, int maxWidth, int maxHeight) {
    if (icon.getIconHeight() <= maxHeight && icon.getIconWidth() <= maxWidth) {
      return icon;
    }

    Image image = IconLoader.toImage(icon, null);
    if (image == null) {
      return icon;
    }

    double scale = 1.0f;
    if (image instanceof JBHiDPIScaledImage) {
      scale = ((JBHiDPIScaledImage)image).getScale();
      image = ((JBHiDPIScaledImage)image).getDelegate();
    }

    BufferedImage bi = ImageUtil.toBufferedImage(Objects.requireNonNull(image));
    Graphics2D g = bi.createGraphics();

    int imageWidth = ImageUtil.getRealWidth(image);
    int imageHeight = ImageUtil.getRealHeight(image);

    maxWidth = maxWidth == Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.round(maxWidth * scale);
    maxHeight = maxHeight == Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.round(maxHeight * scale);
    final int w = Math.min(imageWidth, maxWidth);
    final int h = Math.min(imageHeight, maxHeight);

    final BufferedImage img = ImageUtil.createImage(g, w, h, Transparency.TRANSLUCENT);
    final int offX = imageWidth > maxWidth ? (imageWidth - maxWidth) / 2 : 0;
    final int offY = imageHeight > maxHeight ? (imageHeight - maxHeight) / 2 : 0;
    for (int col = 0; col < w; col++) {
      for (int row = 0; row < h; row++) {
        img.setRGB(col, row, bi.getRGB(col + offX, row + offY));
      }
    }
    g.dispose();
    return new JBImageIcon(RetinaImage.createFrom(img, scale, null));
  }

  public static @NotNull Icon cropIcon(@NotNull Icon icon, @NotNull Rectangle area) {
    if (!new Rectangle(icon.getIconWidth(), icon.getIconHeight()).contains(area)) {
      return icon;
    }
    return new CropIcon(icon, area);
  }

  public static @NotNull Icon flip(final @NotNull Icon icon, final boolean horizontal) {
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

  private static final Function<FileIconKey, Icon> ICON_NULLABLE_FUNCTION = key -> computeFileIcon(key.getFile(), key.getFlags(), key.getProject());

  /**
   * @return a deferred icon for the file, taking into account {@link FileIconProvider} and {@link FileIconPatcher} extensions.
   */
  public static @NotNull Icon computeFileIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project) {
    if (!file.isValid() || project != null && (project.isDisposed() || !wasEverInitialized(project))) {
      return AllIcons.FileTypes.Unknown;
    }

    flags = filterFileIconFlags(file, flags);

    Icon providersIcon = getProvidersIcon(file, flags, project);
    Icon icon = providersIcon != null ? providersIcon : computeBaseFileIcon(file);

    boolean dumb = project != null && DumbService.getInstance(project).isDumb();
    for (FileIconPatcher patcher : FileIconPatcher.EP_NAME.getExtensionList()) {
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
        Registry.is("ide.locked.icon.enabled", false) &&
        (!file.isWritable() || !WritingAccessProvider.isPotentiallyWritable(file, project))) {
      icon = new LayeredIcon(icon, PlatformIcons.LOCKED_ICON);
    }

    LastComputedIconCache.put(file, icon, flags);

    return icon;
  }

  @Iconable.IconFlags
  private static int filterFileIconFlags(@NotNull VirtualFile file, @Iconable.IconFlags int flags) {
    UserDataHolder fileTypeDataHolder = ObjectUtils.tryCast(file.getFileType(), UserDataHolder.class);
    int fileTypeFlagIgnoreMask = ICON_FLAG_IGNORE_MASK.get(fileTypeDataHolder, 0);
    int flagIgnoreMask = ICON_FLAG_IGNORE_MASK.get(file, fileTypeFlagIgnoreMask);
    //noinspection MagicConstant
    return flags & ~flagIgnoreMask;
  }

  /**
   * @return a deferred icon for the file, taking into account {@link FileIconProvider} and {@link FileIconPatcher} extensions.
   * Use {@link #computeFileIcon} where possible (e.g. in background threads) to get a non-deferred icon.
   */
  public static @NotNull Icon getIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, @Nullable Project project) {
    Icon lastIcon = LastComputedIconCache.get(file, flags);
    Icon base = lastIcon != null ? lastIcon : computeBaseFileIcon(file);
    return IconManager.getInstance().createDeferredIcon(base, new FileIconKey(file, project, flags), ICON_NULLABLE_FUNCTION);
  }

  /**
   * @return an icon for a file that's quick to calculate, most likely based on the file type
   * @see #computeFileIcon(VirtualFile, int, Project)
   * @see FileType#getIcon()
   */
  public static @NotNull Icon computeBaseFileIcon(@NotNull VirtualFile vFile) {
    Icon icon = TypePresentationService.getService().getIcon(vFile);
    if (icon != null) {
      return icon;
    }
    FileType fileType = vFile.getFileType();
    if (vFile.isDirectory() && !(fileType instanceof DirectoryFileType)) {
      return IconManager.getInstance().tooltipOnlyIfComposite(PlatformIcons.FOLDER_ICON);
    }
    icon = fileType.getIcon();
    return icon != null ? icon : getEmptyIcon(false);
  }

  private static @Nullable Icon getProvidersIcon(@NotNull VirtualFile file, @Iconable.IconFlags int flags, Project project) {
    for (FileIconProvider provider : FileIconProvider.EP_NAME.getExtensionList()) {
      final Icon icon = provider.getIcon(file, flags, project);
      if (icon != null) return icon;
    }
    return null;
  }

  public static @NotNull Icon getEmptyIcon(boolean showVisibility) {
    com.intellij.ui.icons.RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(EmptyIcon.create(PlatformIcons.CLASS_ICON), 0);
    if (showVisibility) {
      baseIcon.setIcon(EmptyIcon.create(PlatformIcons.PUBLIC_ICON), 1);
    }
    return baseIcon;
  }

  public static @NotNull Image toImage(@NotNull Icon icon) {
    return toImage(icon, null);
  }

  public static @NotNull Image toImage(@NotNull Icon icon, @Nullable ScaleContext context) {
    Image image = IconLoader.toImage(icon, context);
    if (image == null) {
      //noinspection UndesirableClassUsage
      image = new BufferedImage(1, 0, BufferedImage.TYPE_INT_ARGB);
    }
    return image;
  }

  public static @NotNull BufferedImage toBufferedImage(@NotNull Icon icon) {
    return toBufferedImage(icon, false);
  }

  public static @NotNull BufferedImage toBufferedImage(@NotNull Icon icon, boolean inUserScale) {
    return toBufferedImage(icon, null, inUserScale);
  }

  public static @NotNull BufferedImage toBufferedImage(@NotNull Icon icon, @Nullable ScaleContext context, boolean inUserScale) {
    Image image = IconLoader.toImage(icon, context);
    if (image == null) {
      //noinspection UndesirableClassUsage
      image = new BufferedImage(1, 0, BufferedImage.TYPE_INT_ARGB);
    }
    return ImageUtil.toBufferedImage(image, inUserScale);
  }

  public static @NotNull Icon getAddIcon() {
    return AllIcons.General.Add;
  }

  public static @NotNull Icon getRemoveIcon() {
    return AllIcons.General.Remove;
  }

  public static @NotNull Icon getMoveUpIcon() {
    return AllIcons.Actions.MoveUp;
  }

  public static @NotNull Icon getMoveDownIcon() {
    return AllIcons.Actions.MoveDown;
  }

  public static @NotNull Icon getEditIcon() {
    return AllIcons.Actions.Edit;
  }

  public static @NotNull Icon getAddClassIcon() {
    return AllIcons.ToolbarDecorator.AddClass;
  }

  public static @NotNull Icon getAddPatternIcon() {
    return AllIcons.ToolbarDecorator.AddPattern;
  }

  public static @NotNull Icon getAddJiraPatternIcon() {
    return AllIcons.ToolbarDecorator.AddJira;
  }

  public static @NotNull Icon getAddYouTrackPatternIcon() {
    return AllIcons.ToolbarDecorator.AddYouTrack;
  }

  public static @NotNull Icon getAddBlankLineIcon() {
    return AllIcons.ToolbarDecorator.AddBlankLine;
  }

  public static @NotNull Icon getAddPackageIcon() {
    return AllIcons.ToolbarDecorator.AddFolder;
  }

  public static @NotNull Icon getAddLinkIcon() {
    return AllIcons.ToolbarDecorator.AddLink;
  }

  /**
   * @deprecated This icon is not used by platform anymore.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static @NotNull Icon getAnalyzeIcon() {
    return IconLoader.getIcon(getToolbarDecoratorIconsFolder() + "analyze.png", IconUtil.class);
  }

  public static void paintInCenterOf(@NotNull Component c, @NotNull Graphics g, @NotNull Icon icon) {
    final int x = (c.getWidth() - icon.getIconWidth()) / 2;
    final int y = (c.getHeight() - icon.getIconHeight()) / 2;
    icon.paintIcon(c, g, x, y);
  }

  private static @NotNull @NonNls String getToolbarDecoratorIconsFolder() {
    return "/toolbarDecorator/" + (SystemInfoRt.isMac ? "mac/" : "");
  }

  /**
   * Result icons look like original but have equal (maximum) size
   */
  public static Icon @NotNull [] getEqualSizedIcons(Icon @NotNull ... icons) {
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

  public static @NotNull Icon toSize(@Nullable Icon icon, int width, int height) {
    return new IconSizeWrapper(icon, width, height);
  }

  public static void paintSelectionAwareIcon(@NotNull Icon icon, @Nullable JComponent component, @NotNull Graphics g, int x, int y, boolean selected) {
    if (selected) {
      SVGLoader.paintIconWithSelection(icon, component, g, x, y);
    } else {
      icon.paintIcon(component, g, x, y);
    }
  }

  /**
   * Use only for icons under selection
   */
  @ApiStatus.Internal
  @Contract("null -> null; !null -> !null")
  public static @Nullable Icon wrapToSelectionAwareIcon(@Nullable Icon iconUnderSelection) {
    if (iconUnderSelection == null) return null;
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        SVGLoader.paintIconWithSelection(iconUnderSelection, c, g, x, y);
      }

      @Override
      public int getIconWidth() {
        return iconUnderSelection.getIconWidth();
      }

      @Override
      public int getIconHeight() {
        return iconUnderSelection.getIconHeight();
      }
    };
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

  private static final class CropIcon implements Icon {
    private final Icon mySrc;
    private final Rectangle myCrop;

    private CropIcon(@NotNull Icon src, @NotNull Rectangle crop) {
      mySrc = src;
      myCrop = crop;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g = g.create();
      try {
        Rectangle iconClip = new Rectangle(x, y, myCrop.width, myCrop.height);
        Rectangle gClip = g.getClipBounds();
        if (gClip != null) {
          Rectangle2D.intersect(iconClip, gClip, iconClip);
        }
        g.setClip(iconClip);
        mySrc.paintIcon(c, g, x - myCrop.x, y - myCrop.y);
      }
      finally {
        g.dispose();
      }
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + " (" + mySrc + " -> " + myCrop + ")";
    }

    @Override
    public int getIconWidth() {
      return myCrop.width;
    }

    @Override
    public int getIconHeight() {
      return myCrop.height;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CropIcon)) return false;
      CropIcon icon = (CropIcon)o;
      return mySrc.equals(icon.mySrc) &&
             myCrop.equals(icon.myCrop);
    }

    @Override
    public int hashCode() {
      return Objects.hash(mySrc, myCrop);
    }
  }

  /**
   * @deprecated use {@link #scale(Icon, Component, float)}
   */
  @Deprecated
  public static @NotNull Icon scale(final @NotNull Icon source, double _scale) {
    final double scale = clampScale(_scale);
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        paintScaled(c, g, x, y, scale, source);
      }

      @Override
      public int getIconWidth() {
        return (int)(source.getIconWidth() * scale);
      }

      @Override
      public int getIconHeight() {
        return (int)(source.getIconHeight() * scale);
      }
    };
  }

  private static double clampScale(double _scale) {
    return MathUtil.clamp(_scale, 0.1, 32);
  }

  private static void paintScaled(@Nullable Component c, @NotNull Graphics g, int x, int y, double scale, @NotNull Icon source) {
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


  public static @NotNull Icon resizeSquared(@NotNull Icon source, int size) {
    JBValue sizeValue = JBUI.uiIntValue("ResizedIcon", size);
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        double scale = clampScale((double)sizeValue.get() / (double)source.getIconWidth());
        paintScaled(c, g, x, y, scale, source);
      }

      @Override
      public int getIconWidth() {
        return sizeValue.get();
      }

      @Override
      public int getIconHeight() {
        return sizeValue.get();
      }
    };
  }

  /**
   * Returns a copy of the provided {@code icon}.
   *
   * @see CopyableIcon
   */
  public static @NotNull Icon copy(@NotNull Icon icon, @Nullable Component ancestor) {
    return IconLoader.copy(icon, ancestor, false);
  }

  /**
   * Returns a deep copy of the provided {@code icon}.
   *
   * @see CopyableIcon
   */
  public static @NotNull Icon deepCopy(@NotNull Icon icon, @Nullable Component ancestor) {
    return IconLoader.copy(icon, ancestor, true);
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
  public static @NotNull Icon scale(@NotNull Icon icon, @Nullable Component ancestor, float scale) {
    ScaleContext ctx;
    if (ancestor == null && icon instanceof ScaleContextAware) {
      // In this case the icon's context should be preserved, except the OBJ_SCALE.
      UserScaleContext usrCtx = ((ScaleContextAware)icon).getScaleContext();
      ctx = ScaleContext.create(usrCtx);
    } else {
      ctx = ScaleContext.create(ancestor);
    }
    ctx.setScale(OBJ_SCALE.of(scale));
    return scale(icon, ctx);
  }

  /**
   * Returns a scaled icon instance.
   * <p>
   * The passed {@code ctx} is applied to the icon and the {@link ScaleType#OBJ_SCALE} is used to scale it.
   *
   * @see #scale(Icon, Component, float)
   * @param icon the icon to scale
   * @param ctx the scale context to apply
   * @return the scaled icon
   */
  public static @NotNull Icon scale(@NotNull Icon icon, @NotNull ScaleContext ctx) {
    double scale = ctx.getScale(OBJ_SCALE);
    if (icon instanceof CopyableIcon) {
      icon = ((CopyableIcon)icon).deepCopy();
      if (icon instanceof ScalableIcon) {
        if (icon instanceof ScaleContextAware) {
          ctx = ctx.copy();
          // Reset OBJ_SCALE in the context to preserve ScalableIcon.scale(float) implementation
          // from accumulation of the scales: OBJ_SCALE * scale.
          ctx.setScale(OBJ_SCALE.of(1.0));
          ((ScaleContextAware)icon).updateScaleContext(ctx);
        }
        return ((ScalableIcon)icon).scale((float)scale);
      }
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
  public static @NotNull Icon scaleByFont(@NotNull Icon icon, @Nullable Component ancestor, float fontSize) {
    float scale = JBUIScale.getFontScale(fontSize);
    if (icon instanceof ScaleContextAware) {
      ScaleContextAware ctxIcon = (ScaleContextAware)icon;
      // take into account the user scale of the icon
      double usrScale = ctxIcon.getScaleContext().getScale(USR_SCALE);
      scale /= usrScale;
    }
    return scale(icon, ancestor, scale);
  }

  public static Icon scaleByIconWidth(@Nullable Icon icon, @Nullable Component ancestor, @NotNull Icon defaultIcon) {
    return scaleByIcon(icon, ancestor, defaultIcon, Icon::getIconWidth);
  }

  public static Icon scaleByIconHeight(@Nullable Icon icon, @Nullable Component ancestor, @NotNull Icon defaultIcon) {
    return scaleByIcon(icon, ancestor, defaultIcon, Icon::getIconHeight);
  }

  private static Icon scaleByIcon(@Nullable Icon icon, Component ancestor, @NotNull Icon defaultIcon, @NotNull ToIntFunction<Icon> size) {
    if (icon == null || icon == defaultIcon) return defaultIcon;
    int actual = size.applyAsInt(icon);
    int expected = size.applyAsInt(defaultIcon);
    return expected == actual ? icon : scale(icon, ancestor, (float)expected / actual);
  }

  /**
   * Overrides the provided scale in the icon's scale context and in the composited icon's scale contexts (when applicable).
   *
   * @see UserScaleContext#overrideScale(Scale)
   */
  public static @NotNull Icon overrideScale(@NotNull Icon icon, Scale scale) {
    if (icon instanceof CompositeIcon) {
      CompositeIcon compositeIcon = (CompositeIcon)icon;
      for (int i = 0; i < compositeIcon.getIconCount(); i++) {
        Icon subIcon = compositeIcon.getIcon(i);
        if (subIcon != null) overrideScale(subIcon, scale);
      }
    }
    if (icon instanceof ScaleContextAware) {
      ((ScaleContextAware)icon).getScaleContext().overrideScale(scale);
    }
    return icon;
  }

  public static @NotNull Icon colorize(@NotNull Icon source, @NotNull Color color) {
    return colorize(source, color, false);
  }

  public static @NotNull Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color) {
    return colorize(g, source, color, false);
  }

  public static @NotNull Icon colorize(@NotNull Icon source, @NotNull Color color, boolean keepGray) {
    return filterIcon(source, () -> new ColorFilter(color, keepGray), null);
  }

  public static @NotNull Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color, boolean keepGray) {
    return filterIcon(g, source, new ColorFilter(color, keepGray));
  }

  public static @NotNull Icon desaturate(@NotNull Icon source) {
    return filterIcon(source, () -> new DesaturationFilter(), null);
  }

  public static @NotNull Icon brighter(@NotNull Icon source, int tones) {
    return filterIcon(source, () -> new BrighterFilter(tones), null);
  }

  public static @NotNull Icon darker(@NotNull Icon source, int tones) {
    return filterIcon(source, () -> new DarkerFilter(tones), null);
  }

  private static @NotNull Icon filterIcon(Graphics2D g, @NotNull Icon source, @NotNull ColorFilter filter) {
    BufferedImage src = g != null ? ImageUtil.createImage(g, source.getIconWidth(), source.getIconHeight(), BufferedImage.TYPE_INT_ARGB) :
                        ImageUtil.createImage(source.getIconWidth(), source.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = src.createGraphics();
    source.paintIcon(null, g2d, 0, 0);
    g2d.dispose();
    BufferedImage img = g != null ? ImageUtil.createImage(g, source.getIconWidth(), source.getIconHeight(), BufferedImage.TYPE_INT_ARGB) :
                        ImageUtil.createImage(source.getIconWidth(), source.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    int rgba;
    for (int y = 0; y < src.getRaster().getHeight(); y++) {
      for (int x = 0; x < src.getRaster().getWidth(); x++) {
        rgba = src.getRGB(x, y);
        if ((rgba & 0xff000000) != 0) {
          img.setRGB(x, y, filter.filterRGB(x, y, rgba));
        }
      }
    }
    return createImageIcon((Image)img);
  }

  private static final class ColorFilter extends RGBImageFilter {
    private final float[] myBase;
    private final boolean myKeepGray;

    private ColorFilter(@NotNull Color color, boolean keepGray) {
      myKeepGray = keepGray;
      myBase = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    }

    @Override
    public int filterRGB(int x, int y, int rgba) {
      int r = rgba >> 16 & 0xff;
      int g = rgba >> 8 & 0xff;
      int b = rgba & 0xff;
      float[] hsb = new float[3];
      Color.RGBtoHSB(r, g, b, hsb);
      int rgb = Color.HSBtoRGB(myBase[0], myBase[1] * (myKeepGray ? hsb[1] : 1.0f), myBase[2] * hsb[2]);
      return (rgba & 0xff000000) | (rgb & 0xffffff);
    }
  }

  private static class DesaturationFilter extends RGBImageFilter {
    @Override
    public int filterRGB(int x, int y, int rgba) {
      int r = rgba >> 16 & 0xff;
      int g = rgba >> 8 & 0xff;
      int b = rgba & 0xff;
      int min = Math.min(Math.min(r, g), b);
      int max = Math.max(Math.max(r, g), b);
      int grey = (max + min) / 2;
      return (rgba & 0xff000000) | (grey << 16) | (grey << 8) | grey;
    }
  }

  private static class BrighterFilter extends RGBImageFilter {
    private final int myTones;

    BrighterFilter(int tones) {
      myTones = tones;
    }

    @SuppressWarnings("UseJBColor")
    @Override
    public int filterRGB(int x, int y, int rgb) {
      Color originalColor = new Color(rgb, true);
      Color filteredColor = ColorUtil.toAlpha(ColorUtil.brighter(originalColor, myTones), originalColor.getAlpha());
      return filteredColor.getRGB();
    }
  }

  private static class DarkerFilter extends RGBImageFilter {
    private final int myTones;

    DarkerFilter(int tones) {
      myTones = tones;
    }

    @SuppressWarnings("UseJBColor")
    @Override
    public int filterRGB(int x, int y, int rgb) {
      Color originalColor = new Color(rgb, true);
      Color filteredColor = ColorUtil.toAlpha(ColorUtil.darker(originalColor, myTones), originalColor.getAlpha());
      return filteredColor.getRGB();
    }
  }

  /**
   * @deprecated Use {@link #createImageIcon(Image)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static @NotNull JBImageIcon createImageIcon(final @NotNull BufferedImage img) {
    return createImageIcon((Image)img);
  }

  public static @NotNull JBImageIcon createImageIcon(final @NotNull Image img) {
    return new JBImageIcon(img) {
      @Override
      public int getIconWidth() {
        return ImageUtil.getUserWidth(getImage());
      }

      @Override
      public int getIconHeight() {
        return ImageUtil.getUserHeight(getImage());
      }
    };
  }

  public static @NotNull Icon textToIcon(final @NotNull String text, final @NotNull Component component, final float fontSize) {
    final class MyIcon extends JBScalableIcon {
      private final @NotNull String myText;
      private Font myFont;
      private FontMetrics myMetrics;
      private final WeakReference<Component> myCompRef = new WeakReference<>(component);

      private MyIcon(final @NotNull String text) {
        myText = text;
        setIconPreScaled(false);
        getScaleContext().addUpdateListener(() -> update());
        update();
      }

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) { // x,y is in USR_SCALE
        g = g.create();
        try {
          GraphicsUtil.setupAntialiasing(g);
          g.setFont(myFont);
          UIUtil.drawStringWithHighlighting(g, myText,
                                            (int)scaleVal(x, OBJ_SCALE) + (int)scaleVal(2),
                                            (int)scaleVal(y, OBJ_SCALE) + getIconHeight() - (int)scaleVal(1),
                                            JBColor.foreground(), JBColor.background());
        }
        finally {
          g.dispose();
        }
      }

      @Override
      public int getIconWidth() {
        return myMetrics.stringWidth(myText) + (int)scaleVal(4);
      }

      @Override
      public int getIconHeight() {
        return myMetrics.getHeight();
      }

      private void update() {
        myFont = JBFont.create(JBFont.label().deriveFont((float)scaleVal(fontSize, OBJ_SCALE))); // fontSize is in USR_SCALE
        Component comp = myCompRef.get();
        if (comp == null) comp = new Component() {};
        myMetrics = comp.getFontMetrics(myFont);
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MyIcon)) return false;
        final MyIcon icon = (MyIcon)o;

        if (!Objects.equals(myText, icon.myText)) return false;
        if (!Objects.equals(myFont, icon.myFont)) return false;
        return true;
      }
    }

    return new MyIcon(text);
  }

  public static @NotNull Icon addText(@NotNull Icon base, @NotNull String text) {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(base, 0);
    icon.setIcon(textToIcon(text, new JLabel(), JBUIScale.scale(6.0f)), 1, SwingConstants.SOUTH_EAST);
    return icon;
  }

  /**
   * Creates new icon with the filter applied.
   */
  public static @NotNull Icon filterIcon(@NotNull Icon icon, Supplier<? extends RGBImageFilter> filterSupplier, @Nullable Component ancestor) {
    return IconLoader.filterIcon(icon, filterSupplier, ancestor);
  }

  /**
   * This method works with compound icons like RowIcon or LayeredIcon
   * and replaces its inner 'simple' icon with another one recursively
   * @return original icon with modified inner state
   */
  public static Icon replaceInnerIcon(@Nullable Icon icon, @NotNull Icon toCheck, @NotNull Icon toReplace) {
    if (icon  instanceof LayeredIcon) {
      Icon[] layers = ((LayeredIcon)icon).getAllLayers();
      for (int i = 0; i < layers.length; i++) {
        Icon layer = layers[i];
        if (layer == toCheck) {
          layers[i] = toReplace;
        } else {
          replaceInnerIcon(layer, toCheck, toReplace);
        }
      }
    }
    else if (icon instanceof RowIcon) {
      Icon[] allIcons = ((RowIcon)icon).getAllIcons();
      for (int i = 0; i < allIcons.length; i++) {
        Icon anIcon = allIcons[i];
        if (anIcon == toCheck) {
          ((RowIcon)icon).setIcon(toReplace, i);
        }
        else {
          replaceInnerIcon(anIcon, toCheck, toReplace);
        }
      }
    }
    return icon;
  }

  public static @Nullable Icon rowIcon(@Nullable Icon left, @Nullable Icon right) {
    if (left != null && right != null) {
      return new RowIcon(left, right);
    }
    else if (left != null) {
      return left;
    }
    else {
      return right;
    }
  }

  @ApiStatus.Internal
  public static Icon toRetinaAwareIcon(BufferedImage image) {
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        // [tav] todo: the icon is created in def screen scale
        if (UIUtil.isJreHiDPI()) {
          Graphics2D newG = (Graphics2D)g.create(x, y, image.getWidth(), image.getHeight());
          float s = JBUIScale.sysScale();
          newG.scale(1.0 / s, 1.0 / s);
          newG.drawImage(image, 0, 0, null);
          newG.scale(1.0, 1.0);
          newG.dispose();
        }
        else {
          g.drawImage(image, x, y, null);
        }
      }

      @Override
      public int getIconWidth() {
        return UIUtil.isJreHiDPI() ? (int)(image.getWidth() / JBUIScale.sysScale()) : image.getWidth();
      }

      @Override
      public int getIconHeight() {
        return UIUtil.isJreHiDPI() ? (int)(image.getHeight() / JBUIScale.sysScale()) : image.getHeight();
      }
    };
  }
}
