// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.dialog;

import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefURLRequestClient;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.cef.network.CefURLRequest;
import org.cef.network.CefURLRequest.Status;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@ApiStatus.Internal
public class  UrlRequestDialogReply extends JDialog implements CefURLRequestClient {
  private long nativeRef_ = 0;

  private final JLabel statusLabel_ = new JLabel("HTTP-Request status: ");
  private final JTextArea sentRequest_ = new JTextArea();
  private final JTextArea repliedResult_ = new JTextArea();
  private final JButton cancelButton_ = new JButton("Cancel");
  private CefURLRequest urlRequest_ = null;
  private final Frame owner_;
  private final ByteArrayOutputStream byteStream_ = new ByteArrayOutputStream();

  public UrlRequestDialogReply(Frame owner, String title) {
    super(owner, title, false);
    setLayout(new BorderLayout());
    setSize(800, 600);

    owner_ = owner;

    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
    JButton doneButton = new JButton("Done");
    doneButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        urlRequest_.dispose();
        setVisible(false);
        dispose();
      }
    });
    controlPanel.add(doneButton);

    cancelButton_.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (urlRequest_ != null) {
          urlRequest_.cancel();
        }
      }
    });
    cancelButton_.setEnabled(false);
    controlPanel.add(cancelButton_);

    JPanel requestPane = createPanelWithTitle("Sent HTTP-Request", 1, 0);
    requestPane.add(new JScrollPane(sentRequest_));

    JPanel replyPane = createPanelWithTitle("Reply from the server", 1, 0);
    replyPane.add(new JScrollPane(repliedResult_));

    JPanel contentPane = new JPanel(new GridLayout(2, 0));
    contentPane.add(requestPane);
    contentPane.add(replyPane);

    add(statusLabel_, BorderLayout.PAGE_START);
    add(contentPane, BorderLayout.CENTER);
    add(controlPanel, BorderLayout.PAGE_END);
  }

  private static JPanel createPanelWithTitle(String title, int rows, int cols) {
    JPanel result = new JPanel(new GridLayout(rows, cols));
    result.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                                                        BorderFactory.createEmptyBorder(10, 10, 10, 10)));
    return result;
  }

  public void send(CefRequest request) {
    if (request == null) {
      statusLabel_.setText("HTTP-Request status: FAILED");
      sentRequest_.append("Can't send CefRequest because it is NULL");
      cancelButton_.setEnabled(false);
      return;
    }

    urlRequest_ = CefURLRequest.create(request, this);
    if (urlRequest_ == null) {
      statusLabel_.setText("HTTP-Request status: FAILED");
      sentRequest_.append("Can't send CefRequest because creation of CefURLRequest failed.");
      repliedResult_.append(
        "The native code (CEF) returned a NULL-Pointer for CefURLRequest.");
      cancelButton_.setEnabled(false);
    }
    else {
      sentRequest_.append(request.toString());
      cancelButton_.setEnabled(true);
      updateStatus("", false);
    }
  }

  private void updateStatus(final String updateMsg, final boolean printByteStream) {
    final Status status = urlRequest_.getRequestStatus();
    Runnable runnable = () -> {
      statusLabel_.setText("HTTP-Request status: " + status);
      if (status != Status.UR_UNKNOWN && status != Status.UR_IO_PENDING) {
        cancelButton_.setEnabled(false);
      }
      repliedResult_.append(updateMsg);
      if (printByteStream) {
        repliedResult_.append("\n\n" + byteStream_.toString(StandardCharsets.UTF_8));
      }
    };

    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      SwingUtilities.invokeLater(runnable);
    }
  }

  // CefURLRequestClient

  @Override
  public void setNativeRef(String identifer, long nativeRef) {
    nativeRef_ = nativeRef;
  }

  @Override
  public long getNativeRef(String identifer) {
    return nativeRef_;
  }

  @Override
  public void onRequestComplete(CefURLRequest request) {
    String updateStr = "onRequestCompleted\n\n";
    CefResponse response = request.getResponse();
    boolean isText = response.getHeaderByName("Content-Type").startsWith("text");
    updateStr += response.toString();
    updateStatus(updateStr, isText);
  }

  @Override
  public void onUploadProgress(CefURLRequest request, int current, int total) {
    updateStatus("onUploadProgress: " + current + "/" + total + " bytes\n", false);
  }

  @Override
  public void onDownloadProgress(CefURLRequest request, int current, int total) {
    updateStatus("onDownloadProgress: " + current + "/" + total + " bytes\n", false);
  }

  @Override
  public void onDownloadData(CefURLRequest request, byte[] data, int data_length) {
    byteStream_.write(data, 0, data_length);
    updateStatus("onDownloadData: " + data_length + " bytes\n", false);
  }

  @Override
  public boolean getAuthCredentials(boolean isProxy, String host, int port, String realm,
                                    String scheme, CefAuthCallback callback) {
    SwingUtilities.invokeLater(new PasswordDialog(owner_, callback));
    return true;
  }
}
