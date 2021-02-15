// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;

public class SearchWebAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    CopyProvider provider = PlatformDataKeys.COPY_PROVIDER.getData(dataContext);
    if (provider == null) {
      return;
    }
    provider.performCopy(dataContext);
    String string = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    search(string);
  }

  public static void search(String term) {
    if (StringUtil.isNotEmpty(term)) {
      BrowserUtil.browse("http://www.google.com/search?q=" + URLUtil.encodeURIComponent(term));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    CopyProvider provider = PlatformDataKeys.COPY_PROVIDER.getData(dataContext);
    boolean available = provider != null && provider.isCopyEnabled(dataContext) && provider.isCopyVisible(dataContext);
    presentation.setEnabledAndVisible(available);
  }
}
