/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;

import java.awt.datatransfer.DataFlavor;

public class SearchWebAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    CopyProvider provider = PlatformDataKeys.COPY_PROVIDER.getData(dataContext);
    if (provider == null) {
      return;
    }
    provider.performCopy(dataContext);
    String string = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    if (StringUtil.isNotEmpty(string)) {
      BrowserUtil.browse("http://www.google.com/search?q=" + URLUtil.encodeURIComponent(string));
    }
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    CopyProvider provider = PlatformDataKeys.COPY_PROVIDER.getData(dataContext);
    boolean available = provider != null && provider.isCopyEnabled(dataContext) && provider.isCopyVisible(dataContext);
    presentation.setEnabled(available);
    presentation.setVisible(available);
  }
}
