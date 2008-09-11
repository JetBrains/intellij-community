package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ide.DataManager;
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
    return SystemInfo.isWindows || SystemInfo.isMac;
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
      fileUrls.add(index, eachParent.getPresentableUrl());
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
        final File selectedIoFile = new File(selectedValue.getPresentableUrl());
        if (files.indexOf(selectedValue) == 0 && files.size() > 1) {
          open.set(new File(files.get(1).getPresentableUrl()));
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
    String cmd;
    String path;
    if (SystemInfo.isMac) {
      cmd = "open";
      path = ioFile.getAbsolutePath();
    } else if (SystemInfo.isWindows) {
      cmd = "start";
      path = ioFile.getAbsolutePath();
    } else {
      return;
    }

    try {
      File parent = ioFile.getParentFile();
      if (parent != null) {
        Runtime.getRuntime().exec(cmd + " " + path, new String[0], parent);
      } else {
        Runtime.getRuntime().exec(cmd + " " + path);
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private VirtualFile getFile(final AnActionEvent e) {
    return PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
  }

}