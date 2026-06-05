// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.dialog;

import com.intellij.ui.JBColor;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.handler.CefDownloadHandler;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public class  DownloadDialog extends JDialog implements CefDownloadHandler {
  private final Frame owner_;
  private final Map<Integer, DownloadObject> downloadObjects_ = new HashMap<>();
  private final JPanel downloadPanel_ = new JPanel();
  private final DownloadDialog dialog_;

  public DownloadDialog(Frame owner) {
    super(owner, "Downloads", false);
    setVisible(false);
    setSize(400, 300);

    owner_ = owner;
    dialog_ = this;
    downloadPanel_.setLayout(new BoxLayout(downloadPanel_, BoxLayout.Y_AXIS));
    add(downloadPanel_);
  }

  private class DownloadObject extends JPanel {
    private boolean isHidden_ = true;
    private final int identifier_;
    private final JLabel fileName_ = new JLabel();
    private final JLabel status_ = new JLabel();
    private final JButton dlAbort_ = new JButton();
    private final JButton dlRemoveEntry_ = new JButton("x");
    private CefDownloadItemCallback callback_;

    DownloadObject(CefDownloadItem downloadItem, String suggestedName) {
      super();
      setOpaque(true);
      setLayout(new BorderLayout());
      setMaximumSize(new Dimension(dialog_.getWidth() - 10, 80));
      identifier_ = downloadItem.getId();
      Color bgColor = identifier_ % 2 == 0 ? JBColor.WHITE : JBColor.YELLOW;
      setBackground(bgColor);

      fileName_.setText(suggestedName);
      add(fileName_, BorderLayout.NORTH);

      status_.setAlignmentX(LEFT_ALIGNMENT);
      add(status_, BorderLayout.CENTER);

      JPanel controlPane = new JPanel();
      controlPane.setLayout(new BoxLayout(controlPane, BoxLayout.X_AXIS));
      controlPane.setOpaque(true);
      controlPane.setBackground(bgColor);
      dlAbort_.setText("Abort");
      dlAbort_.setEnabled(false);
      dlAbort_.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (callback_ != null) {
            fileName_.setText("ABORTED - " + fileName_.getText());
            callback_.cancel();
          }
        }
      });
      controlPane.add(dlAbort_);

      dlRemoveEntry_.setEnabled(false);
      dlRemoveEntry_.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          DownloadObject removed = downloadObjects_.remove(identifier_);
          if (removed != null) {
            downloadPanel_.remove(removed);
            dialog_.repaint();
          }
        }
      });
      controlPane.add(dlRemoveEntry_);
      add(controlPane, BorderLayout.SOUTH);

      update(downloadItem, null);
    }

    // The method humanReadableByteCount() is based on
    // http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
    String humanReadableByteCount(long bytes) {
      int unit = 1024;
      if (bytes < unit) return bytes + " B";

      int exp = (int)(Math.log(bytes) / Math.log(unit));
      String pre = "" + ("kMGTPE").charAt(exp - 1);
      return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    void update(CefDownloadItem downloadItem, CefDownloadItemCallback callback) {
      int percentComplete = downloadItem.getPercentComplete();
      String rcvBytes = humanReadableByteCount(downloadItem.getReceivedBytes());
      String totalBytes = humanReadableByteCount(downloadItem.getTotalBytes());
      String speed = humanReadableByteCount(downloadItem.getCurrentSpeed()) + "it/s";

      if (downloadItem.getReceivedBytes() >= 5 && isHidden_) {
        dialog_.setVisible(true);
        dialog_.toFront();
        owner_.toBack();
        isHidden_ = false;
      }

      callback_ = callback;
      status_.setText(rcvBytes + " of " + totalBytes + " - " + percentComplete + "%"
                      + " - " + speed);
      dlAbort_.setEnabled(downloadItem.isInProgress());
      dlRemoveEntry_.setEnabled(!downloadItem.isInProgress() || downloadItem.isCanceled()
                                || downloadItem.isComplete());
      if (!downloadItem.isInProgress() && !downloadItem.isCanceled()
          && !downloadItem.isComplete()) {
        fileName_.setText("FAILED - " + fileName_.getText());
        callback.cancel();
      }
    }
  }

  @Override
  public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem,
                                  String suggestedName, CefBeforeDownloadCallback callback) {
    callback.Continue(suggestedName, true);

    DownloadObject dlObject = new DownloadObject(downloadItem, suggestedName);
    downloadObjects_.put(downloadItem.getId(), dlObject);
    downloadPanel_.add(dlObject);

    return true;
  }

  @Override
  public void onDownloadUpdated(
    CefBrowser browser, CefDownloadItem downloadItem, CefDownloadItemCallback callback) {
    DownloadObject dlObject = downloadObjects_.get(downloadItem.getId());
    if (dlObject == null) return;
    dlObject.update(downloadItem, callback);
  }
}
