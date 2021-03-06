// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public Icon getIcon(@NotNull String path, @NotNull Class<?> aClass) {
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
  public @NotNull Icon loadRasterizedIcon(@NotNull String path, @NotNull ClassLoader classLoader, long cacheKey, int flags) {
    assert !path.isEmpty() && path.charAt(0) != '/';
    return new IconWithToolTipImpl(path, createRasterizedImageDataLoader(path, classLoader, cacheKey, flags));
  }

  // reflective path is not supported
  // result is not cached
  @SuppressWarnings("DuplicatedCode")
  private static @NotNull ImageDataLoader createRasterizedImageDataLoader(@NotNull String path, @NotNull ClassLoader classLoader, long cacheKey, int imageFlags) {
    long startTime = StartUpMeasurer.getCurrentTimeIfEnabled();
    Pair<String, ClassLoader> patchedPath = IconLoader.patchPath(path, classLoader);
    String effectivePath = path;
    if (patchedPath != null) {
      // not safe for now to decide should patchPath return path with leading slash or not
      effectivePath = patchedPath.first.startsWith("/") ? patchedPath.first.substring(1) : patchedPath.first;
      if (patchedPath.second != null) {
        classLoader = patchedPath.second;
      }
    }

    ImageDataLoader resolver = new RasterizedImageDataLoader(effectivePath, classLoader, cacheKey, imageFlags);
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

  @NotNull
  @Override
  public Icon createEmptyIcon(@NotNull Icon icon) {
    return EmptyIcon.create(icon);
  }

  @NotNull
  @Override
  public <T> Icon createDeferredIcon(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> f) {
    return IconDeferrer.getInstance().defer(base, param, f);
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

  @NotNull
  @Override
  public com.intellij.ui.icons.RowIcon createRowIcon(int iconCount, com.intellij.ui.icons.RowIcon.Alignment alignment) {
    return new RowIcon(iconCount, alignment);
  }

  @NotNull
  @Override
  public com.intellij.ui.icons.RowIcon createRowIcon(Icon @NotNull ... icons) {
    return new RowIcon(icons);
  }

  @NotNull
  @Override
  public RowIcon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags) {
    List<Icon> layersFromProviders = new ArrayList<>();
    for (IconLayerProvider provider : IconLayerProvider.EP_NAME.getExtensionList()) {
      final Icon layerIcon = provider.getLayerIcon(instance, BitUtil.isSet(flags, FLAGS_LOCKED));
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

  @NotNull
  @Override
  public Icon createOffsetIcon(@NotNull Icon icon) {
    return new OffsetIcon(icon);
  }

  @NotNull
  @Override
  public Icon colorize(Graphics2D g, @NotNull Icon source, @NotNull Color color) {
    return IconUtil.colorize(g, source, color);
  }

  @NotNull
  @Override
  public Icon createLayered(Icon @NotNull ... icons) {
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
    @NotNull
    private final Icon icon;

    private IconLayer(final int flagMask, @NotNull Icon icon) {
      BitUtil.assertOneBitMask(flagMask);
      this.flagMask = flagMask;
      this.icon = icon;
    }
  }

  private static final class IconDescriptionLoader implements Supplier<String> {
    private final String myPath;
    private String myResult;
    private boolean myCalculated;

    private IconDescriptionLoader(String path) {
      myPath = path;
    }

    @Override
    public String get() {
      if (!myCalculated) {
        myResult = findIconDescription(myPath);
        myCalculated = true;
      }
      return myResult;
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
