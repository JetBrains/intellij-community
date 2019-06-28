/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.images.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SVGLoader;
import org.intellij.images.fileTypes.impl.SvgFileType;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertSvgToPngAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile svgFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
    try {
      Image image = SVGLoader.load(new File(svgFile.getPath()).toURI().toURL(), 1f);
      String path = svgFile.getPath();
      ImageIO.write((BufferedImage)image, "png", new File(path + ".png"));
    }
    catch (IOException e1) {
      e1.printStackTrace();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean enabled = file != null && FileTypeRegistry.getInstance().isFileOfType(file, SvgFileType.INSTANCE);
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
