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
package com.intellij.application.options.codeStyle;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.options.SchemeExporter;
import com.intellij.openapi.options.SchemeExporterEP;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * @author Rustam Vishnyakov
 */
class CodeStyleSchemeExporterUI {
  @NotNull private final Component myParentComponent;
  @NotNull private final CodeStyleScheme myScheme;
  @NotNull private final StatusCallback myStatusCallback;

  CodeStyleSchemeExporterUI(@NotNull Component parentComponent, @NotNull CodeStyleScheme scheme, @NotNull StatusCallback statusCallback) {
    myParentComponent = parentComponent;
    myScheme = scheme;
    myStatusCallback = statusCallback;
  }

  void export() {
    ListPopup popup = JBPopupFactory.getInstance().createListPopup(
      new BaseListPopupStep<String>(ApplicationBundle.message("scheme.exporter.ui.export.as.title"), enumExporters()) {
        @Override
        public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
          return doFinalStep(() -> exportSchemeUsing(selectedValue));
        }
      });
    popup.showInCenterOf(myParentComponent);
  }

  private static String[] enumExporters() {
    List<String> names = new ArrayList<>();
    Collection<SchemeExporterEP<CodeStyleScheme>> extensions = SchemeExporterEP.getExtensions(CodeStyleScheme.class);
    for (SchemeExporterEP<CodeStyleScheme> extension : extensions) {
      names.add(extension.name);
    }
    return ArrayUtil.toStringArray(names);
  }

  private void exportSchemeUsing(@NotNull String exporterName) {
    SchemeExporter<CodeStyleScheme> exporter = SchemeExporterEP.getExporter(exporterName, CodeStyleScheme.class);
    if (exporter != null) {
      String ext = exporter.getExtension();
      FileSaverDialog saver =
        FileChooserFactory.getInstance()
          .createSaveFileDialog(new FileSaverDescriptor(
            ApplicationBundle.message("scheme.exporter.ui.file.chooser.title"),
            ApplicationBundle.message("scheme.exporter.ui.file.chooser.message"),
            ext), myParentComponent);
      VirtualFileWrapper target = saver.save(null, getFileNameSuggestion() + "." + ext);
      if (target != null) {
        VirtualFile targetFile = target.getVirtualFile(true);
        String message;
        MessageType messageType;
        if (targetFile != null) {
          final AccessToken writeToken = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
          try {
            OutputStream outputStream = targetFile.getOutputStream(this);
            try {
              exporter.exportScheme(myScheme, outputStream);
            }
            finally {
              outputStream.close();
            }
            message = ApplicationBundle
              .message("scheme.exporter.ui.code.style.exported.message", myScheme.getName(), targetFile.getPresentableUrl());
            messageType = MessageType.INFO;
          }
          catch (Exception e) {
            message = ApplicationBundle.message("scheme.exporter.ui.export.failed", e.getMessage());
            messageType = MessageType.ERROR;
          }
          finally {
            writeToken.finish();
          }
        }
        else {
          message = ApplicationBundle.message("scheme.exporter.ui.cannot.write.message");
          messageType = MessageType.ERROR;
        }
        myStatusCallback.showMessage(message, messageType);
      }
    }
  }

  private String getFileNameSuggestion() {
    return myScheme.getName();
  }

  interface StatusCallback {
    void showMessage(@NotNull String message, @NotNull MessageType messageType);
  }
}
