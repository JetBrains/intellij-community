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
package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ShowFilePathAction extends AnAction {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ShowFilePathAction");

  @Override
  public void update(final AnActionEvent e) {
    if (!isSupported()) {
      e.getPresentation().setVisible(false);
      return;
    }
    e.getPresentation().setEnabled(getFile(e) != null);
  }

  public static boolean isSupported() {
    return isJava6() || SystemInfo.isMac;
  }

  private static boolean isJava6() {
    return System.getProperty("java.version").startsWith("1.6");
  }

  public void actionPerformed(final AnActionEvent e) {
    show(getFile(e), new ShowAction() {
      public void show(final ListPopup popup) {
        final DataContext context = DataManager.getInstance().getDataContext();
        popup.showInBestPositionFor(context);
      }
    });
  }

  public static void show(final VirtualFile file, final MouseEvent e) {
    show(file, new ShowAction() {
      public void show(final ListPopup popup) {
        if (!e.getComponent().isShowing()) return;
        popup.show(new RelativePoint(e));
      }
    });
  }

  public static void show(final VirtualFile file, final ShowAction show) {
    if (!isSupported()) return;

    final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    final ArrayList<String> fileUrls = new ArrayList<String>();
    VirtualFile eachParent = file;
    while (eachParent != null) {
      final int index = files.size() == 0 ? 0 : files.size();
      files.add(index, eachParent);
      fileUrls.add(index, getPresentableUrl(eachParent));
      if (eachParent.getParent() == null && eachParent.getFileSystem() instanceof JarFileSystem) {
        eachParent = JarFileSystem.getInstance().getVirtualFileForJar(eachParent);
        if (eachParent == null) break;
      }
      eachParent = eachParent.getParent();
    }


    final ArrayList<Icon> icons = new ArrayList<Icon>();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        for (String each : fileUrls) {
          final File ioFile = new File(each);
          Icon eachIcon;
          if (ioFile.exists()) {
            eachIcon = FileSystemView.getFileSystemView().getSystemIcon(ioFile);
          } else {
            eachIcon = new EmptyIcon(16, 16);
          }

          icons.add(eachIcon);
        }

        LaterInvocator.invokeLater(new Runnable() {
          public void run() {
            show.show(createPopup(files, icons));
          }
        });
      }
    });
  }

  private static String getPresentableUrl(final VirtualFile eachParent) {
    String url = eachParent.getPresentableUrl();
    if (eachParent.getParent() == null && SystemInfo.isWindows) {
      url += "\\";
    }
    return url;
  }

  interface ShowAction {
    void show(ListPopup popup);
  }

  private static ListPopup createPopup(final ArrayList<VirtualFile> files, final ArrayList<Icon> icons) {
    final BaseListPopupStep<VirtualFile> step = new BaseListPopupStep<VirtualFile>("File Path", files, icons) {
      @NotNull
      @Override
      public String getTextFor(final VirtualFile value) {
        return value.getPresentableName();
      }

      @Override
      public PopupStep onChosen(final VirtualFile selectedValue, final boolean finalChoice) {
        final Ref<File> open = new Ref<File>();
        final Ref<File> toSelect = new Ref<File>();
        final File selectedIoFile = new File(getPresentableUrl(selectedValue));
        if (files.indexOf(selectedValue) == 0 && files.size() > 1) {
          open.set(new File(getPresentableUrl(files.get(1))));
          toSelect.set(selectedIoFile);
        } else {
          open.set(selectedIoFile);
        }
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            open(open.get(), toSelect.get());
          }
        });
        return FINAL_CHOICE;
      }
    };

    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    return popup;
  }

  private static void open(final File ioFile, File toSelect) {
    if (SystemInfo.isMac) {
      String cmd = "open";
      String path = ioFile.getAbsolutePath();
      try {
        File parent = ioFile.getParentFile();
        if (parent != null) {
          Runtime.getRuntime().exec(cmd + " " + path, ArrayUtil.EMPTY_STRING_ARRAY, parent);
        } else {
          Runtime.getRuntime().exec(cmd + " " + path);
        }
      }
      catch (IOException e) {
        LOG.warn(e);
      }

    } else if (isJava6()) {
      try {
        final Object desktopObject = Class.forName("java.awt.Desktop").getMethod("getDesktop").invoke(null);
        desktopObject.getClass().getMethod("open", File.class).invoke(desktopObject, ioFile);
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    } else {
      throw new UnsupportedOperationException();
    }

  }

  private VirtualFile getFile(final AnActionEvent e) {
    return PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
  }

}