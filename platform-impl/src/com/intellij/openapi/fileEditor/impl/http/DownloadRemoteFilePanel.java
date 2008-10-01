package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.FileDownloadingListener;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.util.net.HTTPProxySettingsDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author nik
 */
public class DownloadRemoteFilePanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.http.DownloadRemoteFilePanel");
  private static final Icon ERROR_ICON = IconLoader.getIcon("/runConfigurations/configurationWarning.png");
  @NonNls private static final String ERROR_CARD = "error";
  @NonNls private static final String DOWNLOADING_CARD = "downloading";
  private JPanel myMainPanel;
  private JLabel myProgressLabel;
  private JProgressBar myProgressBar;
  private JButton myCancelButton;
  private JPanel myContentPanel;
  private JLabel myErrorLabel;
  private JButton myTryAgainButton;
  private JButton myChangeProxySettingsButton;
  private final Project myProject;
  private final HttpVirtualFile myVirtualFile;
  private MergingUpdateQueue myProgressUpdatesQueue;
  private MyDownloadingListener myDownloadingListener;

  public DownloadRemoteFilePanel(final Project project, final HttpVirtualFile virtualFile) {
    myProject = project;
    myVirtualFile = virtualFile;
    myErrorLabel.setIcon(ERROR_ICON);
    myProgressUpdatesQueue = new MergingUpdateQueue("downloading progress updates", 300, false, myMainPanel);

    final RemoteFileInfo remoteFileInfo = virtualFile.getFileInfo();
    myDownloadingListener = new MyDownloadingListener();
    remoteFileInfo.addDownloadingListener(myDownloadingListener);
    myCancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        remoteFileInfo.cancelDownloading();
      }
    });

    myTryAgainButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        showCard(DOWNLOADING_CARD);
        remoteFileInfo.restartDownloading();
      }
    });
    myChangeProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        new HTTPProxySettingsDialog().show();
      }
    });
    showCard(DOWNLOADING_CARD);
    remoteFileInfo.startDownloading();
    if (remoteFileInfo.isDownloaded()) {
      switchEditor();
    }
    else {
      String errorMessage = remoteFileInfo.getErrorMessage();
      if (errorMessage != null) {
        myDownloadingListener.errorOccured(errorMessage);
      }
    }
  }

  private void showCard(final String name) {
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, name);
  }

  private void switchEditor() {
    LOG.debug("Switching editor...");
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
        fileEditorManager.closeFile(myVirtualFile);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            //todo[nik] use ActionCallback
            fileEditorManager.openFile(myVirtualFile, true);
            LOG.debug("Editor for downloaded file opened.");
          }
        });
      }
    });
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void showNotify() {
    myProgressUpdatesQueue.showNotify();
  }

  public void hideNotify() {
    myProgressUpdatesQueue.hideNotify();
  }

  public void dispose() {
    myVirtualFile.getFileInfo().removeDownloadingListener(myDownloadingListener);
    myProgressUpdatesQueue.dispose();
  }

  private class MyDownloadingListener implements FileDownloadingListener {
    public void fileDownloaded(final VirtualFile localFile) {
      switchEditor();
    }

    public void errorOccured(@NotNull final String errorMessage) {
      myErrorLabel.setText(errorMessage);
      showCard(ERROR_CARD);
    }

    public void progressMessageChanged(final boolean indeterminate, @NotNull final String message) {
      myProgressUpdatesQueue.queue(new Update("progress text") {
        public void run() {
          myProgressLabel.setText(message);
        }
      });
    }

    public void progressFractionChanged(final double fraction) {
      myProgressUpdatesQueue.queue(new Update("fraction") {
        public void run() {
          myProgressBar.setValue((int)Math.round(100*fraction));
        }
      });
    }
  }
}
