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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class ElementBase extends UserDataHolderBase implements Iconable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.ElementBase");

  public static final int FLAGS_LOCKED = 0x800;
  private static final NullableFunction<ElementIconRequest,Icon> ICON_COMPUTE = request -> {
    PsiElement element = request.myPointer.getElement();
    if (element == null) return null;

    Icon icon = computeIconNow(element, request.myFlags);
    LastComputedIcon.put(element, icon, request.myFlags);
    return icon;
  };

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
      return computeIcon(flags);
    }
    catch (ProcessCanceledException | IndexNotReadyException e) {
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
        baseIcon = computeBaseIcon(flags);
      }
      return IconDeferrer.getInstance().defer(baseIcon, new ElementIconRequest(psiElement, psiElement.getProject(), flags), ICON_COMPUTE);
    }

    return computeIconNow(psiElement, flags);
  }

  @Nullable
  private static Icon computeIconNow(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
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
    if (BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
      return new RowIcon(icon, VISIBILITY_ICON_PLACEHOLDER.getValue());
    }
    return icon;
  }

  protected boolean isVisibilitySupported() {
    return false;
  }

  @NotNull
  public static Icon overlayIcons(@NotNull Icon ... icons) {
    final LayeredIcon icon = new LayeredIcon(icons.length);
    int i = 0;
    for (Icon ic : icons) {
      icon.setIcon(ic, i++);
    }
    return icon;
  }

  @NotNull
  public static RowIcon buildRowIcon(final Icon baseIcon, Icon visibilityIcon) {
    RowIcon icon = new RowIcon(2);
    icon.setIcon(baseIcon, 0);
    icon.setIcon(visibilityIcon, 1);
    return icon;
  }

  public static Icon iconWithVisibilityIfNeeded(@Iconable.IconFlags int flags, Icon baseIcon, Icon visibility) {
    return BitUtil.isSet(flags, ICON_FLAG_VISIBILITY) ? buildRowIcon(baseIcon, visibility) : baseIcon;
  }

  private static class ElementIconRequest {
    private final SmartPsiElementPointer<?> myPointer;
    @Iconable.IconFlags private final int myFlags;

    private ElementIconRequest(@NotNull PsiElement element, @NotNull Project project, @IconFlags int flags) {
      myPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element);
      myFlags = flags;
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
    final boolean isLocked = BitUtil.isSet(flags, ICON_FLAG_READ_STATUS) && !element.isWritable();
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

  @NotNull
  public static RowIcon createLayeredIcon(@NotNull Iconable instance, Icon icon, int flags) {
    List<Icon> layersFromProviders = new SmartList<>();
    for (IconLayerProvider provider : Extensions.getExtensions(IconLayerProvider.EP_NAME)) {
      final Icon layerIcon = provider.getLayerIcon(instance, BitUtil.isSet(flags, FLAGS_LOCKED));
      if (layerIcon != null) {
        layersFromProviders.add(layerIcon);
      }
    }
    if (flags != 0 || !layersFromProviders.isEmpty()) {
      List<Icon> iconLayers = new SmartList<>();
      for(IconLayer l: ourIconLayers) {
        if (BitUtil.isSet(flags, l.flagMask)) {
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
    int flags = BitUtil.clear(_flags, ICON_FLAG_READ_STATUS);
    final boolean isLocked = BitUtil.isSet(_flags, ICON_FLAG_READ_STATUS) && !element.isWritable();
    if (isLocked) flags |= FLAGS_LOCKED;
    return flags;
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

  private static final List<IconLayer> ourIconLayers = ContainerUtil.createLockFreeCopyOnWriteList();

  public static void registerIconLayer(int flagMask, @NotNull Icon icon) {
    for(IconLayer iconLayer: ourIconLayers) {
      if (iconLayer.flagMask == flagMask) return;
    }
    ourIconLayers.add(new IconLayer(flagMask, icon));
  }
}
