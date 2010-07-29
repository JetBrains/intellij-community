/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.IconDeferrer;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.*;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.update.ComparableObject;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ElementBase extends UserDataHolderBase implements Iconable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.ElementBase");

  public static final int FLAGS_LOCKED = 0x800;
  private Map<Integer, Icon> myBaseIcon;

  private static final Icon VISIBILITY_ICON_PLACHOLDER = new EmptyIcon(Icons.PUBLIC_ICON);
  private static final Icon ICON_PLACHOLDER = IconLoader.getIcon("/nodes/nodePlaceholder.png");

  @Nullable
  public Icon getIcon(int flags) {
    if (!(this instanceof PsiElement)) return null;

    try {
      Icon icon = computeIcon(flags);
      Iconable.LastComputedIcon.put(this, icon, flags);
      return icon;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (IndexNotReadyException e) {
      throw e;
    }
    catch(Exception e) {
      LOG.error(e);
        return null;
      }
    }

  private Icon computeIcon(final int flags) {
    PsiElement psiElement = (PsiElement)this;
    Icon baseIcon = LastComputedIcon.get(psiElement, flags);
    if (baseIcon == null) {
      if (myBaseIcon == null) {
        myBaseIcon = new HashMap<Integer, Icon>(3);
      }
      if (!myBaseIcon.containsKey(flags)) {
        myBaseIcon.put(flags, computeBaseIcon(flags));
      }
      baseIcon = myBaseIcon.get(flags);
    }

    if (isToDeferIconLoading()) {
      return IconDeferrer.getInstance().defer(baseIcon, new ElementIconRequest(psiElement, flags), new Function<ElementIconRequest, Icon>() {
        public Icon fun(ElementIconRequest request) {
          return computeIconNow(request.getElement(), request.getFlags());
        }
      });
    } else {
      return computeIconNow(psiElement, flags);
    }
  }

  protected boolean isToDeferIconLoading() {
    return Registry.is("psi.deferIconLoading");
  }

  private Icon computeIconNow(PsiElement element, int flags) {
    if (!element.isValid()) return null;
    final Icon providersIcon = PsiIconUtil.getProvidersIcon(element, flags);
    if (providersIcon != null) {
      return providersIcon instanceof RowIcon ? (RowIcon)providersIcon : createLayeredIcon(providersIcon, flags);
    }
    return getElementIcon(flags);
  }

  protected Icon computeBaseIcon(int flags) {
    return isVisibilitySupported() ? getAdjustedBaseIcon(getBaseIcon(), flags) : getBaseIcon();
  }

  protected Icon getBaseIcon() {
    return ICON_PLACHOLDER;
  }

  protected Icon getAdjustedBaseIcon(Icon icon, int flags) {
    Icon result = icon;
    if ((flags & Iconable.ICON_FLAG_VISIBILITY) > 0) {
      RowIcon rowIcon = new RowIcon(2);
      rowIcon.setIcon(icon, 0);
      rowIcon.setIcon(VISIBILITY_ICON_PLACHOLDER, 1);
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

  public static Icon iconWithVisibilityIfNeeded(int flags, Icon baseIcon, Icon visibility) {
    return (flags & ICON_FLAG_VISIBILITY) != 0 ? buildRowIcon(
      baseIcon,
      visibility
    ):baseIcon;
  }

  public static class ElementIconRequest extends ComparableObject.Impl {
    public ElementIconRequest(PsiElement element, int flags) {
      super(new Object[] {element, flags});
    }

    public PsiElement getElement() {
      return (PsiElement)getEqualityObjects()[0];
    }

    public int getFlags() {
      return (Integer)getEqualityObjects()[1];
    }
  }

  protected Icon getElementIcon(final int flags) {
    final PsiElement element = (PsiElement)this;

    if (!element.isValid()) return null;

    RowIcon baseIcon;
    final boolean isLocked = (flags & ICON_FLAG_READ_STATUS) != 0 && !element.isWritable();
    int elementFlags = isLocked ? FLAGS_LOCKED : 0;
    if (element instanceof ItemPresentation && ((ItemPresentation)element).getIcon(false) != null) {
        baseIcon = createLayeredIcon(((ItemPresentation)element).getIcon(false), elementFlags);
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
      return createLayeredIcon(fileTypeIcon, elementFlags);
    }
    else {
      return null;
    }
    return baseIcon;
  }

  public static RowIcon createLayeredIcon(Icon icon, int flags) {
    if (flags != 0) {
      List<Icon> iconLayers = new SmartList<Icon>();
      for(IconLayer l: ourIconLayers) {
        if ((flags & l.flagMask) != 0) {
          iconLayers.add(l.icon);
        }
      }
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

  public static int transformFlags(PsiElement element, int _flags) {
    int flags = _flags & ~(ICON_FLAG_READ_STATUS);
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

  private static final List<IconLayer> ourIconLayers = new ArrayList<IconLayer>();

  public static void registerIconLayer(int flagMask, Icon icon) {
    for(IconLayer iconLayer: ourIconLayers) {
      if (iconLayer.flagMask == flagMask) return;
    }
    ourIconLayers.add(new IconLayer(flagMask, icon));
  }

  static {
    registerIconLayer(FLAGS_LOCKED, Icons.LOCKED_ICON);
  }
}