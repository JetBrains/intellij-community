// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconLayerProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.icons.IconLoadMeasurer;
import com.intellij.ui.icons.ImageDataLoader;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class CoreIconManager implements IconManager, CoreAwareIconManager {
  private static final List<IconLayer> iconLayers = new CopyOnWriteArrayList<>();
  private static final int FLAGS_LOCKED = 0x800;
  private static final Logger LOG = Logger.getInstance(CoreIconManager.class);

  @Override
  public @NotNull Icon getStubIcon() {
    return AllIcons.Actions.Stub;
  }

  @Override
  public @NotNull Icon getIcon(@NotNull String path, @NotNull Class<?> aClass) {
    Icon icon = IconLoader.getIcon(path, aClass);
    Supplier<String> tooltip = new IconDescriptionLoader(path);
    if (icon instanceof ScalableIcon) {
      return new ScalableIconWrapperWithToolTip((ScalableIcon)icon, tooltip);
    }
    else {
      return new IconWrapperWithToolTip(icon, tooltip);
    }
  }

  @Override
  public @NotNull Icon loadRasterizedIcon(@NotNull String path, @NotNull ClassLoader classLoader, int cacheKey, int flags) {
    assert !path.isEmpty() && path.charAt(0) != '/';
    return new IconWithToolTipImpl(path, createRasterizedImageDataLoader(path, classLoader, cacheKey, flags));
  }

  // reflective path is not supported
  // result is not cached
  @SuppressWarnings("DuplicatedCode")
  private static @NotNull ImageDataLoader createRasterizedImageDataLoader(@NotNull String path,
                                                                          @NotNull ClassLoader classLoader,
                                                                          int cacheKey,
                                                                          int imageFlags) {
    long startTime = StartUpMeasurer.getCurrentTimeIfEnabled();
    Pair<String, ClassLoader> patchedPath = IconLoader.patchPath(path, classLoader);
    ImageDataLoader resolver;
    WeakReference<ClassLoader> classLoaderWeakRef = new WeakReference<>(classLoader);
    if (patchedPath == null) {
      resolver = new RasterizedImageDataLoader(path, classLoaderWeakRef, path, classLoaderWeakRef, cacheKey, imageFlags);
    }
    else {
      // not safe for now to decide should patchPath return path with leading slash or not
      resolver = RasterizedImageDataLoader.createPatched(path, classLoaderWeakRef, patchedPath, cacheKey, imageFlags);
    }
    if (startTime != -1) {
      IconLoadMeasurer.findIcon.end(startTime);
    }
    return resolver;
  }

  private static final class IconWithToolTipImpl extends IconLoader.CachedImageIcon implements IconWithToolTip {
    private String result;
    private boolean isTooltipCalculated;

    IconWithToolTipImpl(@NotNull String originalPath, @NotNull ImageDataLoader resolver) {
      super(originalPath, resolver, null, null);
    }

    @Override
    public @NlsContexts.Tooltip @Nullable String getToolTip(boolean composite) {
      if (!isTooltipCalculated) {
        result = findIconDescription(Objects.requireNonNull(getOriginalPath()));
        isTooltipCalculated = true;
      }
      //noinspection HardCodedStringLiteral
      return result;
    }
  }

  @Override
  public @NotNull Icon createEmptyIcon(@NotNull Icon icon) {
    return EmptyIcon.create(icon);
  }

  @Override
  public @NotNull <T> Icon createDeferredIcon(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> iconProducer) {
    return IconDeferrer.getInstance().defer(base, param, iconProducer);
  }

  @Override
  public void registerIconLayer(int flagMask, @NotNull Icon icon) {
    for (IconLayer iconLayer : iconLayers) {
      if (iconLayer.flagMask == flagMask) {
        return;
      }
    }
    iconLayers.add(new IconLayer(flagMask, icon));
  }

  @Override
  public @NotNull Icon tooltipOnlyIfComposite(@NotNull Icon icon) {
    return new IconWrapperWithToolTipComposite(icon);
  }

  @Override
  public @NotNull com.intellij.ui.icons.RowIcon createRowIcon(int iconCount, com.intellij.ui.icons.RowIcon.Alignment alignment) {
    return new RowIcon(iconCount, alignment);
  }

  @Override
  public @NotNull com.intellij.ui.icons.RowIcon createRowIcon(Icon @NotNull ... icons) {
    return new RowIcon(icons);
  }

  @Override
  public @NotNull RowIcon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags) {
    List<Icon> layersFromProviders = new ArrayList<>();
    for (IconLayerProvider provider : IconLayerProvider.EP_NAME.getExtensionList()) {
      Icon layerIcon = provider.getLayerIcon(instance, BitUtil.isSet(flags, FLAGS_LOCKED));
      if (layerIcon != null) {
        layersFromProviders.add(layerIcon);
      }
    }
    if (flags != 0 || !layersFromProviders.isEmpty()) {
      List<Icon> iconLayers = new ArrayList<>();
      for (IconLayer l : CoreIconManager.iconLayers) {
        if (BitUtil.isSet(flags, l.flagMask)) {
          iconLayers.add(l.icon);
        }
      }
      iconLayers.addAll(layersFromProviders);
      LayeredIcon layeredIcon = new LayeredIcon(1 + iconLayers.size());
      layeredIcon.setIcon(icon, 0);
      for (int i = 0; i < iconLayers.size(); i++) {
        Icon icon1 = iconLayers.get(i);
        layeredIcon.setIcon(icon1, i + 1);
      }
      icon = layeredIcon;
    }

    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(icon, 0);
    return baseIcon;
  }

  @Override
  public @NotNull Icon createOffsetIcon(@NotNull Icon icon) {
    return new OffsetIcon(icon);
  }

  @Override
  public @NotNull Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color) {
    return IconUtil.colorize(g, source, color);
  }

  @Override
  public @NotNull Icon createLayered(Icon @NotNull ... icons) {
    return new LayeredIcon(icons);
  }

  @Override
  public @NotNull Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
    return IconUtil.getIcon(file, flags, project);
  }

  @Override
  public @NotNull Runnable wakeUpNeo(@NotNull Object reason) {
    return MacUtil.wakeUpNeo(reason);
  }

  private static final class IconLayer {
    private final int flagMask;
    private final @NotNull Icon icon;

    private IconLayer(final int flagMask, @NotNull Icon icon) {
      BitUtil.assertOneBitMask(flagMask);
      this.flagMask = flagMask;
      this.icon = icon;
    }
  }

  private static final class IconDescriptionLoader implements Supplier<String> {
    private final String path;
    private String result;
    private boolean isCalculated;

    private IconDescriptionLoader(String path) {
      this.path = path;
    }

    @Override
    public String get() {
      if (!isCalculated) {
        result = findIconDescription(path);
        isCalculated = true;
      }
      return result;
    }
  }

  private static @Nullable String findIconDescription(@NotNull String path) {
    String pathWithoutExt = Strings.trimEnd(path, ".svg");
    String key = "icon." + (pathWithoutExt.startsWith("/") ? pathWithoutExt.substring(1) : pathWithoutExt).replace('/', '.') + ".tooltip";
    Ref<String> result = new Ref<>();
    IconDescriptionBundleEP.EP_NAME.processWithPluginDescriptor((ep, descriptor) -> {
      ClassLoader classLoader = descriptor == null ? null : descriptor.getPluginClassLoader();
      if (classLoader == null) {
        classLoader = CoreIconManager.class.getClassLoader();
      }
      ResourceBundle bundle = DynamicBundle.INSTANCE.getResourceBundle(ep.resourceBundle, classLoader);
      String description = AbstractBundle.messageOrNull(bundle, key);
      if (description != null) {
        result.set(description);
      }
    });
    if (result.get() == null && Registry.is("ide.icon.tooltips.trace.missing", false)) {
      LOG.info("Icon tooltip requested but not found for " + path);
    }
    return result.get();
  }
}
