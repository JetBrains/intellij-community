/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.ide.IconProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;import java.awt.*;import java.awt.image.BufferedImage;import java.awt.image.PixelGrabber;


public class IconUtil {
  private static IconProvider[] ourIconProviders = null;

  public static Icon getIcon(VirtualFile file, int flags, Project project) {
    Icon icon = getBaseIcon(file, flags, project);

    Icon excludedIcon = null;
    if (project != null) {
      final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      if (projectFileIndex.isInSource(file) && CompilerManager.getInstance(project).isExcludedFromCompilation(file)) {
        excludedIcon = Icons.EXCLUDED_FROM_COMPILE_ICON;
      }
    }

    Icon lockedIcon = null;
    if ((flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !file.isWritable()) {
      lockedIcon = Icons.LOCKED_ICON;
    }

    if (excludedIcon != null || lockedIcon != null) {
      LayeredIcon layeredIcon = new LayeredIcon(1 + (lockedIcon != null ? 1 : 0) + (excludedIcon != null ? 1 : 0));
      int layer = 0;
      layeredIcon.setIcon(icon, layer++);
      if (lockedIcon != null) {
        layeredIcon.setIcon(lockedIcon, layer++);
      }
      if (excludedIcon != null) {
        layeredIcon.setIcon(excludedIcon, layer);
      }
      icon = layeredIcon;
    }
    return icon;
  }

  public static Icon getBaseIcon(final VirtualFile file, final int flags, final Project project) {
    Icon providersIcon = getProvidersIcon(file, flags, project);
    Icon icon = providersIcon == null ? file.getIcon() : providersIcon;

    if (project != null) {
      final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      final boolean isUnderSource = projectFileIndex.isJavaSourceFile(file);
      FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
      if (fileType == StdFileTypes.JAVA) {
        if (!isUnderSource) {
          icon = Icons.JAVA_OUTSIDE_SOURCE_ICON;
        }
        else {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          if (psiFile instanceof PsiJavaFile) {
            PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
            if (classes.length != 0) {
              // prefer icon of the class named after file
              final String fileName = file.getNameWithoutExtension();
              for (PsiClass aClass : classes) {
                icon = aClass.getIcon(flags);
                if (Comparing.strEqual(aClass.getName(), fileName)) break;
              }
            }
          }
        }
      }

    }
    return icon;
  }

  public static Icon getProvidersIcon(VirtualFile file, int flags, Project project) {
    if(project == null) return null;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);

    return psiFile == null ? null : getProvidersIcon(psiFile, flags);
  }

  public static Icon getProvidersIcon(PsiElement element, int flags) {
    for (final IconProvider iconProvider : getIconProviders()) {
      final Icon icon = iconProvider.getIcon(element, flags);
      if (icon != null) return icon;
    }
    return null;
  }

  public static IconProvider[] getIconProviders() {
    if (ourIconProviders == null) {
      ourIconProviders = ApplicationManager.getApplication().getComponents(IconProvider.class);
    }
    return ourIconProviders;
  }
  public static Icon markWithError(Icon baseIcon) {
    LayeredIcon icon = new LayeredIcon(2);
    Icon error = IconLoader.getIcon("/nodes/errorMark.png");
    icon.setIcon(error,0);
    icon.setIcon(redden(baseIcon),1, error.getIconWidth(), 0);
    return icon;
  }
  public static Icon redden(Icon icon) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();

    BufferedImage compatibleImage = gc.createCompatibleImage(icon.getIconWidth(),icon.getIconHeight(),Transparency.TRANSLUCENT);
    Graphics g = compatibleImage.getGraphics();
    icon.paintIcon(new JComponent(){},g, 0,0);
    //g.drawImage(tempImage,0,0,null);

    g.dispose();
    Image redden = reddenImage(compatibleImage, icon.getIconWidth(), icon.getIconHeight());
    return IconLoader.getIcon(redden);
  }
  public static Image reddenImage(Image image, final int width, final int height) {
    PixelGrabber grabber = new PixelGrabber(image, 0, 0, -1, -1, true);
    try {
      grabber.grabPixels();
    }
    catch (InterruptedException e) {
      //
    }
    int[] pixels = (int[])grabber.getPixels();
    System.arraycopy(pixels, 0, pixels=new int[pixels.length],0,pixels.length);
    for (int i = 0; i < pixels.length; i++) {
      int pixel = pixels[i];
      int alpha = (pixel >> 24) & 0xff;
      if (alpha == 0) continue;
      int red = (pixel >> 16) & 0xFF;
      red = 0xff;
      int green = (pixel >> 8) & 0xFF;
      int blue = (pixel >> 0) & 0xFF;
      int value;
      value = ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | ((blue & 0xFF) << 0);
      //float[] hsb = Color.RGBtoHSB(red, green, blue, null);
      //value = ((alpha & 0xFF) << 24) | Color.HSBtoRGB(hsb[0], Math.min(1,hsb[1]*1.4f), hsb[2]);
      pixels[i] = value;
    }

    final BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    buffered.setRGB(0,0, width, height, pixels, 0, width);
    image = buffered;
    return image;
  }
}
