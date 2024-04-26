// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.LastComputedIconCache;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.CoreAwareIconManager;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.BitUtil;
import com.intellij.util.FileIconUtil;
import com.intellij.util.PsiIconUtil;
import com.intellij.util.ui.EDT;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ElementBase extends UserDataHolderBase implements Iconable {
  private static final Logger LOG = Logger.getInstance(ElementBase.class);

  public static final int FLAGS_LOCKED = 0x800;
  private static final Function1<ElementIconRequest,Icon> ICON_COMPUTE = request -> {
    PsiElement element = request.myPointer.getElement();
    if (element == null) {
      return null;
    }

    Icon icon = computeIconNow(element, request.myFlags);
    LastComputedIconCache.put(element, icon, request.myFlags);
    return icon;
  };

  private static final NotNullLazyValue<Icon> VISIBILITY_ICON_PLACEHOLDER = NotNullLazyValue.createValue(() -> {
    IconManager iconManager = IconManager.getInstance();
    return iconManager.createEmptyIcon(iconManager.getPlatformIcon(PlatformIcons.Public));
  });

  @Override
  public @Nullable Icon getIcon(int flags) {
    if (!(this instanceof PsiElement)) {
      return null;
    }

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

  private @Nullable Icon computeIcon(@Iconable.IconFlags int flags) {
    PsiElement psiElement = (PsiElement)this;
    if (!psiElement.isValid()) return null;

    if (Registry.is("psi.deferIconLoading", true) && EDT.isCurrentThreadEdt()) {
      Icon baseIcon = LastComputedIconCache.get(psiElement, flags);
      if (baseIcon == null) {
        baseIcon = AstLoadingFilter.disallowTreeLoading(() -> computeBaseIcon(flags));
      }
      if (baseIcon == null) {
        return null;
      }
      return IconManager.getInstance().createDeferredIcon(
        baseIcon, new ElementIconRequest(psiElement, psiElement.getProject(), flags), ICON_COMPUTE);
    }

    return computeIconNow(psiElement, flags);
  }

  private static @Nullable Icon computeIconNow(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
    return AstLoadingFilter.disallowTreeLoading(() -> doComputeIconNow(element, flags));
  }

  private static Icon doComputeIconNow(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
    if (!(element instanceof ElementBase)) {
      throw new AssertionError(element.getClass().getName() + " is not ElementBase");
    }
    Icon icon = PsiIconUtil.getIconFromProviders(element, flags);
    if (element instanceof PsiFileSystemItem) {
      VirtualFile file = PsiUtilCore.getVirtualFile(element);
      if (file != null && icon == null) {
        icon = FileIconUtil.getIconFromProviders(file, flags, element.getProject());
      }
      if (file != null && icon != null) {
        icon = FileIconUtil.patchIconByIconPatchers(icon, file, flags, element.getProject());
      }
      if (icon instanceof RowIcon) {
        return icon;
      }
      else if (icon != null) {
        return IconManager.getInstance().createLayeredIcon(element, icon, flags);
      }
      return ((ElementBase)element).getElementIcon(flags);
    }
    else if (icon == null) {
      return ((ElementBase)element).getElementIcon(flags);
    }
    else if (icon instanceof RowIcon) {
      return icon;
    }
    else {
      return IconManager.getInstance().createLayeredIcon(element, icon, flags);
    }
  }

  protected @Nullable Icon computeBaseIcon(@Iconable.IconFlags int flags) {
    Icon baseIcon = isVisibilitySupported() ? getAdjustedBaseIcon(getBaseIcon(), flags) : getBaseIcon();

    // to prevent blinking, base icon should be created with the layers
    if (baseIcon != null && this instanceof PsiElement) {
      PsiFile file = ((PsiElement)this).getContainingFile();
      if (file != null) {
        return IconManager.getInstance().createLayeredIcon(file, baseIcon, flags);
      }
    }
    return baseIcon;
  }

  protected @Nullable Icon getBaseIcon() {
    if (this instanceof PsiElement) {
      PsiFile file = ((PsiElement)this).getContainingFile();
      if (file != null) {
        if (!isNativeFileType(file.getFileType())) {
          try {
            return file.getFileType().getIcon();
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      }
    }
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.NodePlaceholder);
  }

  public static boolean isNativeFileType(FileType fileType) {
    return fileType instanceof INativeFileType && ((INativeFileType) fileType).useNativeIcon() || fileType instanceof UnknownFileType;
  }

  protected Icon getAdjustedBaseIcon(@Nullable Icon icon, @Iconable.IconFlags int flags) {
    if (icon != null && BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
      return IconManager.getInstance().createRowIcon(icon, VISIBILITY_ICON_PLACEHOLDER.getValue());
    }
    return icon;
  }

  protected boolean isVisibilitySupported() {
    return false;
  }

  public static @NotNull RowIcon buildRowIcon(Icon baseIcon, Icon visibilityIcon) {
    return IconManager.getInstance().createRowIcon(baseIcon, visibilityIcon);
  }

  public static Icon iconWithVisibilityIfNeeded(@Iconable.IconFlags int flags, Icon baseIcon, Icon visibility) {
    return BitUtil.isSet(flags, ICON_FLAG_VISIBILITY) ? buildRowIcon(baseIcon, visibility) : baseIcon;
  }

  private static final class ElementIconRequest {
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
      if (myPointer != request.myPointer) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = System.identityHashCode(myPointer);
      result = 31 * result + myFlags;
      return result;
    }
  }

  protected @Nullable Icon getElementIcon(@Iconable.IconFlags int flags) {
    PsiElement element = (PsiElement)this;
    if (!element.isValid()) {
      return null;
    }

    boolean isLocked = BitUtil.isSet(flags, ICON_FLAG_READ_STATUS) && !element.isWritable();
    int elementFlags = isLocked ? FLAGS_LOCKED : 0;

    if (element instanceof ItemPresentation) {
      Icon baseIcon = ((ItemPresentation)element).getIcon(false);
      if (baseIcon != null) {
        return IconManager.getInstance().createLayeredIcon(this, baseIcon, elementFlags);
      }
    }

    if (element instanceof PsiFileSystemItem) {
      VirtualFile file = PsiUtilCore.getVirtualFile(element);
      Icon baseIcon = null;
      if (file == null && element instanceof PsiFile) {
        baseIcon = ((PsiFile)element).getFileType().getIcon();
      }
      else if (file != null) {
        IconManager iconManager = IconManager.getInstance();
        if (iconManager instanceof CoreAwareIconManager) {
          baseIcon = ((CoreAwareIconManager)iconManager).getIcon(file, flags & ~ICON_FLAG_READ_STATUS, element.getProject());
        }
        else {
          baseIcon = isVisibilitySupported() ? getAdjustedBaseIcon(getBaseIcon(), flags) : getBaseIcon();
        }
      }
      if (baseIcon == null) return null;
      return IconManager.getInstance().createLayeredIcon(this, baseIcon, elementFlags);
    }

    return null;
  }

  public static int transformFlags(PsiElement element, @IconFlags int _flags) {
    int flags = BitUtil.clear(_flags, ICON_FLAG_READ_STATUS);
    boolean isLocked = BitUtil.isSet(_flags, ICON_FLAG_READ_STATUS) && !element.isWritable();
    if (isLocked) flags |= FLAGS_LOCKED;
    return flags;
  }
}
