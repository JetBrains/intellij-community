/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconLayerProvider;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.IconDeferrer;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class ElementBase extends UserDataHolderBase implements Iconable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.ElementBase");

  public static final int FLAGS_LOCKED = 0x800;
  private static final NullableFunction<ElementIconRequest,Icon> ICON_COMPUTE = new NullableFunction<ElementIconRequest, Icon>() {
    @Override
    public Icon fun(ElementIconRequest request) {
      final PsiElement element = request.getElement();
      if (element == null || !element.isValid()) return null;
      if (element.getProject().isDisposed()) return null;
      return computeIconNow(element, request.getFlags());
    }
  };
  private static final Key<TIntObjectHashMap<Icon>> BASE_ICONS = Key.create("BASE_ICONS");

  private static final NotNullLazyValue<Icon> VISIBILITY_ICON_PLACEHOLDER = new NotNullLazyValue<Icon>() {
    @NotNull
    @Override
    protected Icon compute() {
      return EmptyIcon.create(PlatformIcons.PUBLIC_ICON);
    }
  };

  public static final NotNullLazyValue<Icon> ICON_PLACEHOLDER = new NotNullLazyValue<Icon>() {
    @NotNull
    @Override
    protected Icon compute() {
      return AllIcons.Nodes.NodePlaceholder;
    }
  };

  @Override
  @Nullable
  public Icon getIcon(int flags) {
    if (!(this instanceof PsiElement)) return null;

    try {
      Icon icon = computeIcon(flags);
      LastComputedIcon.put(this, icon, flags);
      return icon;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (IndexNotReadyException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  private Icon computeIcon(@Iconable.IconFlags int flags) {
    PsiElement psiElement = (PsiElement)this;
    if (!psiElement.isValid()) return null;

    if (Registry.is("psi.deferIconLoading")) {
      Icon baseIcon = LastComputedIcon.get(psiElement, flags);
      if (baseIcon == null) {
        TIntObjectHashMap<Icon> cache = getUserData(BASE_ICONS);
        if (cache == null) {
          cache = putUserDataIfAbsent(BASE_ICONS, new TIntObjectHashMap<Icon>());
        }
        synchronized (cache) {
          if (!cache.containsKey(flags)) {
            cache.put(flags, computeBaseIcon(flags));
          }
          baseIcon = cache.get(flags);
        }
      }
      return IconDeferrer.getInstance().defer(baseIcon, new ElementIconRequest(psiElement, flags), ICON_COMPUTE);
    }

    return computeIconNow(psiElement, flags);
  }

  @Nullable
  private static Icon computeIconNow(PsiElement element, @Iconable.IconFlags int flags) {
    final Icon providersIcon = PsiIconUtil.getProvidersIcon(element, flags);
    if (providersIcon != null) {
      return providersIcon instanceof RowIcon ? (RowIcon)providersIcon : createLayeredIcon(element, providersIcon, flags);
    }
    return ((ElementBase)element).getElementIcon(flags);
  }

  protected Icon computeBaseIcon(@Iconable.IconFlags int flags) {
    return isVisibilitySupported() ? getAdjustedBaseIcon(getBaseIcon(), flags) : getBaseIcon();
  }

  protected Icon getBaseIcon() {
    if (this instanceof PsiElement) {
      PsiFile file = ((PsiElement)this).getContainingFile();
      if (file != null) {
        if (!isNativeFileType(file.getFileType())) {
          return file.getFileType().getIcon();
        }
      }
    }
    return ICON_PLACEHOLDER.getValue();
  }

  public static boolean isNativeFileType(FileType fileType) {
    return fileType instanceof INativeFileType && ((INativeFileType) fileType).useNativeIcon() || fileType instanceof UnknownFileType;
  }

  protected Icon getAdjustedBaseIcon(Icon icon, @Iconable.IconFlags int flags) {
    Icon result = icon;
    if ((flags & ICON_FLAG_VISIBILITY) > 0) {
      RowIcon rowIcon = new RowIcon(2);
      rowIcon.setIcon(icon, 0);
      rowIcon.setIcon(VISIBILITY_ICON_PLACEHOLDER.getValue(), 1);
      result = rowIcon;
    }

    return result;
  }

  protected boolean isVisibilitySupported() {
    return false;
  }

  public static Icon overlayIcons(Icon ... icons) {
    final LayeredIcon icon = new LayeredIcon(icons.length);
    int i = 0;
    for(Icon ic:icons) icon.setIcon(ic, i++);
    return icon;
  }

  public static RowIcon buildRowIcon(final Icon baseIcon, Icon visibilityIcon) {
    RowIcon icon = new RowIcon(2);
    icon.setIcon(baseIcon, 0);
    icon.setIcon(visibilityIcon, 1);
    return icon;
  }

  public static Icon iconWithVisibilityIfNeeded(@Iconable.IconFlags int flags, Icon baseIcon, Icon visibility) {
    return (flags & ICON_FLAG_VISIBILITY) != 0 ? buildRowIcon(
      baseIcon,
      visibility
    ):baseIcon;
  }

  private static class ElementIconRequest {
    private final SmartPsiElementPointer<?> myPointer;
    @Iconable.IconFlags private final int myFlags;

    public ElementIconRequest(PsiElement element, @Iconable.IconFlags int flags) {
      myPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
      myFlags = flags;
    }

    @Nullable
    public PsiElement getElement() {
      if (myPointer.getProject().isDisposed()) return null;
      PsiElement element = myPointer.getElement();
      SmartPointerManager.getInstance(myPointer.getProject()).removePointer(myPointer);
      return element;
    }

    @Iconable.IconFlags
    public int getFlags() {
      return myFlags;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ElementIconRequest)) return false;

      ElementIconRequest request = (ElementIconRequest)o;

      if (myFlags != request.myFlags) return false;
      if (!myPointer.equals(request.myPointer)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myPointer.hashCode();
      result = 31 * result + myFlags;
      return result;
    }
  }

  @Nullable
  protected Icon getElementIcon(@Iconable.IconFlags int flags) {
    final PsiElement element = (PsiElement)this;

    if (!element.isValid()) return null;

    RowIcon baseIcon;
    final boolean isLocked = (flags & ICON_FLAG_READ_STATUS) != 0 && !element.isWritable();
    int elementFlags = isLocked ? FLAGS_LOCKED : 0;
    if (element instanceof ItemPresentation && ((ItemPresentation)element).getIcon(false) != null) {
        baseIcon = createLayeredIcon(this, ((ItemPresentation)element).getIcon(false), elementFlags);
    }
    else if (element instanceof PsiFile) {
      PsiFile file = (PsiFile)element;

      VirtualFile virtualFile = file.getVirtualFile();
      final Icon fileTypeIcon;
      if (virtualFile == null) {
        fileTypeIcon = file.getFileType().getIcon();
      }
      else {
        fileTypeIcon = IconUtil.getIcon(virtualFile, flags & ~ICON_FLAG_READ_STATUS, file.getProject());
      }
      return createLayeredIcon(this, fileTypeIcon, elementFlags);
    }
    else {
      return null;
    }
    return baseIcon;
  }

  public static RowIcon createLayeredIcon(Iconable instance, Icon icon, int flags) {
    List<Icon> layersFromProviders = new SmartList<Icon>();
    for (IconLayerProvider provider : Extensions.getExtensions(IconLayerProvider.EP_NAME)) {
      final Icon layerIcon = provider.getLayerIcon(instance, (flags & FLAGS_LOCKED) != 0);
      if (layerIcon != null) {
        layersFromProviders.add(layerIcon);
      }
    }
    if (flags != 0 || !layersFromProviders.isEmpty()) {
      List<Icon> iconLayers = new SmartList<Icon>();
      for(IconLayer l: ourIconLayers) {
        if ((flags & l.flagMask) != 0) {
          iconLayers.add(l.icon);
        }
      }
      iconLayers.addAll(layersFromProviders);
      LayeredIcon layeredIcon = new LayeredIcon(1 + iconLayers.size());
      layeredIcon.setIcon(icon, 0);
      for (int i = 0; i < iconLayers.size(); i++) {
        Icon icon1 = iconLayers.get(i);
        layeredIcon.setIcon(icon1, i+1);
      }
      icon = layeredIcon;
    }
    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(icon, 0);
    return baseIcon;
  }

  public static int transformFlags(PsiElement element, @IconFlags int _flags) {
    int flags = _flags & ~ICON_FLAG_READ_STATUS;
    final boolean isLocked = (_flags & ICON_FLAG_READ_STATUS) != 0 && !element.isWritable();
    if (isLocked) flags |= FLAGS_LOCKED;
    return flags;
  }

  private static class IconLayer {
    int flagMask;
    Icon icon;

    IconLayer(final int flagMask, final Icon icon) {
      this.flagMask = flagMask;
      this.icon = icon;
    }
  }

  private static final List<IconLayer> ourIconLayers = ContainerUtil.createLockFreeCopyOnWriteList();

  public static void registerIconLayer(int flagMask, Icon icon) {
    for(IconLayer iconLayer: ourIconLayers) {
      if (iconLayer.flagMask == flagMask) return;
    }
    ourIconLayers.add(new IconLayer(flagMask, icon));
  }
}
