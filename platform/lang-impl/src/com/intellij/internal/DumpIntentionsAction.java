// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.internal;

import com.intellij.codeInsight.intention.impl.config.IntentionActionMetaData;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class DumpIntentionsAction extends AnAction implements DumbAware {
  DumpIntentionsAction() {
    super(ActionsBundle.messagePointer("action.DumpIntentionsAction.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    VirtualFile file = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                              event.getProject(),
                                              null);
    if (file == null) {
      return;
    }

    File root = VfsUtilCore.virtualToIoFile(file);
    Element el = new Element("root");
    Map<String, Element> categoryMap = new HashMap<>();
    for (IntentionActionMetaData metaData : IntentionManagerSettings.getInstance().getMetaData()) {
      try {
        Element metadataElement = new Element("intention");
        metadataElement.setAttribute("family", metaData.getFamily());
        metadataElement.setAttribute("description", metaData.getDescription().getText());

        String key = StringUtil.join(metaData.myCategory, ".");
        Element element = getCategoryElement(categoryMap, el, metaData, key, metaData.myCategory.length - 1);
        element.addContent(metadataElement);
      }
      catch (IOException e1) {
        e1.printStackTrace();
      }
    }

    try {
      JDOMUtil.write(el, root.toPath().resolve("intentions.xml"));
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static Element getCategoryElement(Map<String, Element> categoryMap, Element rootElement, IntentionActionMetaData metaData, String key, int idx) {
    Element element = categoryMap.get(key);
    if (element == null) {

      element = new Element("category");
      element.setAttribute("name", metaData.myCategory[idx]);
      categoryMap.put(key, element);
      if (idx == 0) {
        rootElement.addContent(element);
      } else {
        getCategoryElement(categoryMap, rootElement, metaData, StringUtil.join(metaData.myCategory, 0, metaData.myCategory.length - 1, "."), idx - 1).addContent(element);
      }
    }
    return element;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }
}