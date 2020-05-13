// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.ide.IconLayerProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CoreIconManager implements IconManager {
  private static final List<IconLayer> ourIconLayers  = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final int FLAGS_LOCKED = 0x800;
  private static final Logger LOG = Logger.getInstance(CoreIconManager.class);

  @NotNull
  @Override
  public Icon getIcon(@NotNull String path, @NotNull Class aClass) {
    Icon icon = IconLoader.getIcon(path, aClass);
    return IconWithToolTip.create(icon, new IconDescriptionLoader(path));
  }

  @NotNull
  @Override
  public Icon createEmptyIcon(@NotNull Icon icon) {
    return EmptyIcon.create(icon);
  }

  @NotNull
  @Override
  public <T> Icon createDeferredIcon(@NotNull Icon base, T param, @NotNull Function<? super T, ? extends Icon> f) {
    return IconDeferrer.getInstance().defer(base, param, t -> f.apply(t));
  }

  @NotNull
  @Override
  public Icon getAnalyzeIcon() {
    return IconUtil.getAnalyzeIcon();
  }

  @Override
  public void registerIconLayer(int flagMask, @NotNull Icon icon) {
    for (IconLayer iconLayer : ourIconLayers) {
      if (iconLayer.flagMask == flagMask) return;
    }
    ourIconLayers.add(new IconLayer(flagMask, icon));
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
    List<Icon> layersFromProviders = new SmartList<>();
    for (IconLayerProvider provider : IconLayerProvider.EP_NAME.getExtensionList()) {
      final Icon layerIcon = provider.getLayerIcon(instance, BitUtil.isSet(flags, FLAGS_LOCKED));
      if (layerIcon != null) {
        layersFromProviders.add(layerIcon);
      }
    }
    if (flags != 0 || !layersFromProviders.isEmpty()) {
      List<Icon> iconLayers = new SmartList<>();
      for (IconLayer l : ourIconLayers) {
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

  private static class IconLayer {
    private final int flagMask;
    @NotNull
    private final Icon icon;

    private IconLayer(final int flagMask, @NotNull Icon icon) {
      BitUtil.assertOneBitMask(flagMask);
      this.flagMask = flagMask;
      this.icon = icon;
    }
  }

  private static class IconDescriptionLoader implements Supplier<String> {
    private final String myPath;
    private String myResult;
    private boolean myCalculated;

    private IconDescriptionLoader(String path) {
      myPath = path;
    }

    @Override
    public String get() {
      if (!myCalculated) {
        myResult = findIconDescription();
        myCalculated = true;
      }
      return myResult;
    }

    private String findIconDescription() {
      String basePath = StringUtil.trimStart(StringUtil.trimEnd(myPath, ".svg"), "/");
      String key = "icon." + basePath.replace('/', '.') + ".tooltip";
      Ref<String> result = new Ref<>();
      IconDescriptionBundleEP.EP_NAME.processWithPluginDescriptor((ep, descriptor) -> {
        ClassLoader classLoader = descriptor == null ? null : descriptor.getPluginClassLoader();
        if (classLoader == null) classLoader = getClass().getClassLoader();
        ResourceBundle bundle = DynamicBundle.INSTANCE.getResourceBundle(ep.qualifiedName, classLoader);
        String description = AbstractBundle.messageOrNull(bundle, key);
        if (description != null) {
          result.set(description);
        }
      });
      if (result.get() == null && Registry.is("ide.icon.tooltips.trace.missing")) {
        LOG.info("Icon tooltip requested but not found for " + myPath);
      }
      return result.get();
    }
  }
}
