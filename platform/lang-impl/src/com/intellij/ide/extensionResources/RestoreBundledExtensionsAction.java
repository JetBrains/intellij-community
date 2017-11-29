/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.extensionResources;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class RestoreBundledExtensionsAction extends DumbAwareAction {

  @Override
  public void update(AnActionEvent e) {
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    boolean isDirectory = file != null && file.isDirectory();
    e.getPresentation().setEnabledAndVisible(isDirectory && ExtensionsRootType.getInstance().getPath(file) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ExtensionsRootType extensionsRootType = ExtensionsRootType.getInstance();

    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    PluginId pluginId = extensionsRootType.getOwner(file);
    String path = extensionsRootType.getPath(file);

    assert file != null && pluginId != null && path != null;

    try {
      extensionsRootType.extractBundledResources(pluginId, path);
    }
    catch (IOException ex) {
      ExtensionsRootType.LOG.warn("Failed to extract bundled extensions for " + file.getPath(), ex);
    }
  }
}
