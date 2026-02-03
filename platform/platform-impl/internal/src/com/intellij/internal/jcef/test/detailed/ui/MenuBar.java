// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a slightly modified version of test 'tests.detailed.MainFrame' from repository https://github.com/JetBrains/jcef.git
package com.intellij.internal.jcef.test.detailed.ui;

import com.intellij.internal.jcef.test.detailed.BrowserFrame;
import com.intellij.internal.jcef.test.detailed.MainFrame;
import com.intellij.internal.jcef.test.detailed.dialog.*;
import com.intellij.internal.jcef.test.detailed.util.DataUri;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefDevToolsClient;
import org.cef.callback.CefPdfPrintCallback;
import org.cef.callback.CefRunFileDialogCallback;
import org.cef.callback.CefStringVisitor;
import org.cef.handler.CefDialogHandler.FileDialogMode;
import org.cef.misc.CefPdfPrintSettings;
import org.cef.network.CefCookieManager;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("ALL")
@ApiStatus.Internal
public class  MenuBar extends JMenuBar {
  static class SaveAs implements CefStringVisitor {
    private final PrintWriter fileWriter_;

    SaveAs(String fName) throws FileNotFoundException, UnsupportedEncodingException {
      fileWriter_ = new PrintWriter(fName, "UTF-8");
    }

    @Override
    public void visit(String string) {
      fileWriter_.write(string);
      fileWriter_.close();
    }
  }

  private final MainFrame owner_;
  private final CefBrowser browser_;
  private String last_selected_file_ = "";
  private final JMenu bookmarkMenu_;
  private final ControlPanel control_pane_;
  private final DownloadDialog downloadDialog_;
  private final CefCookieManager cookieManager_;
  private boolean reparentPending_ = false;
  private CefDevToolsClient devToolsClient_;

  public MenuBar(MainFrame owner, CefBrowser browser, ControlPanel control_pane,
                 DownloadDialog downloadDialog, CefCookieManager cookieManager) {
    owner_ = owner;
    browser_ = browser;
    control_pane_ = control_pane;
    downloadDialog_ = downloadDialog;
    cookieManager_ = cookieManager;

    setEnabled(browser_ != null);

    JMenu fileMenu = new JMenu("File");

    JMenuItem openFileItem = new JMenuItem("Open file...");
    openFileItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent arg0) {
        JFileChooser fc = new JFileChooser(new File(last_selected_file_));
        // Show open dialog; this method does not return until the dialog is closed.
        fc.showOpenDialog(owner_);
        File selectedFile = fc.getSelectedFile();
        if (selectedFile != null) {
          last_selected_file_ = selectedFile.getAbsolutePath();
          browser_.loadURL("file:///" + selectedFile.getAbsolutePath());
        }
      }
    });
    fileMenu.add(openFileItem);

    JMenuItem openFileDialog = new JMenuItem("Save as...");
    openFileDialog.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CefRunFileDialogCallback callback = new CefRunFileDialogCallback() {
          @Override
          public void onFileDialogDismissed(Vector<String> filePaths) {
            if (!filePaths.isEmpty()) {
              try {
                SaveAs saveContent = new SaveAs(filePaths.get(0));
                browser_.getSource(saveContent);
              }
              catch (FileNotFoundException | UnsupportedEncodingException e) {
                browser_.executeJavaScript("alert(\"Can't save file\");",
                                           control_pane_.getAddress(), 0);
              }
            }
          }
        };
        browser_.runFileDialog(FileDialogMode.FILE_DIALOG_SAVE, owner_.getTitle(),
                               "index.html", null, callback);
      }
    });
    fileMenu.add(openFileDialog);

    JMenuItem printItem = new JMenuItem("Print...");
    printItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        browser_.print();
      }
    });
    fileMenu.add(printItem);

    JMenuItem printToPdfItem = new JMenuItem("Print to PDF");
    printToPdfItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.showSaveDialog(owner_);
        File selectedFile = fc.getSelectedFile();
        if (selectedFile != null) {
          CefPdfPrintSettings pdfSettings = new CefPdfPrintSettings();
          pdfSettings.display_header_footer = true;
          // letter page size
          pdfSettings.paper_width = 8.5;
          pdfSettings.paper_height = 11;
          browser.printToPDF(
            selectedFile.getAbsolutePath(), pdfSettings, new CefPdfPrintCallback() {
              @Override
              public void onPdfPrintFinished(String path, boolean ok) {
                SwingUtilities.invokeLater(() -> {
                  if (ok) {
                    JOptionPane.showMessageDialog(owner_,
                                                  "PDF saved to " + path, "Success",
                                                  JOptionPane.INFORMATION_MESSAGE);
                  }
                  else {
                    JOptionPane.showMessageDialog(owner_, "PDF failed",
                                                  "Failed", JOptionPane.ERROR_MESSAGE);
                  }
                });
              }
            });
        }
      }
    });
    fileMenu.add(printToPdfItem);

    JMenuItem searchItem = new JMenuItem("Search...");
    searchItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        new SearchDialog(owner_, browser_).setVisible(true);
      }
    });
    fileMenu.add(searchItem);

    fileMenu.addSeparator();

    JMenuItem viewSource = new JMenuItem("View source");
    viewSource.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        browser_.viewSource();
      }
    });
    fileMenu.add(viewSource);

    JMenuItem getSource = new JMenuItem("Get source...");
    getSource.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ShowTextDialog visitor = new ShowTextDialog(
          owner_, "Source of \"" + control_pane_.getAddress() + "\"");
        browser_.getSource(visitor);
      }
    });
    fileMenu.add(getSource);

    JMenuItem getText = new JMenuItem("Get text...");
    getText.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ShowTextDialog visitor = new ShowTextDialog(
          owner_, "Content of \"" + control_pane_.getAddress() + "\"");
        browser_.getText(visitor);
      }
    });
    fileMenu.add(getText);

    fileMenu.addSeparator();

    JMenuItem showDownloads = new JMenuItem("Show Downloads");
    showDownloads.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        downloadDialog_.setVisible(true);
      }
    });
    fileMenu.add(showDownloads);

    JMenuItem showCookies = new JMenuItem("Show Cookies");
    showCookies.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CookieManagerDialog cookieManager =
          new CookieManagerDialog(owner_, "Cookie Manager", cookieManager_);
        cookieManager.setVisible(true);
      }
    });
    fileMenu.add(showCookies);

    fileMenu.addSeparator();

    JMenuItem exitItem = new JMenuItem("Exit");
    exitItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        owner_.dispatchEvent(new WindowEvent(owner_, WindowEvent.WINDOW_CLOSING));
      }
    });
    fileMenu.add(exitItem);

    bookmarkMenu_ = new JMenu("Bookmarks");

    JMenuItem addBookmarkItem = new JMenuItem("Add bookmark");
    addBookmarkItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        addBookmark(owner_.getTitle(), control_pane_.getAddress());
      }
    });
    bookmarkMenu_.add(addBookmarkItem);
    bookmarkMenu_.addSeparator();

    JMenu testMenu = new JMenu("Tests");

    JMenuItem testJSItem = new JMenuItem("JavaScript alert");
    testJSItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        browser_.executeJavaScript("alert('Hello World');", control_pane_.getAddress(), 1);
      }
    });
    testMenu.add(testJSItem);

    JMenuItem jsAlertItem = new JMenuItem("JavaScript alert (will be suppressed)");
    jsAlertItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        browser_.executeJavaScript("alert('Never displayed');", "http://dontshow.me", 1);
      }
    });
    testMenu.add(jsAlertItem);

    JMenuItem testShowText = new JMenuItem("Show Text");
    testShowText.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        browser_.loadURL(DataUri.create(
          "text/html", "<html><body><h1>Hello World</h1></body></html>"));
      }
    });
    testMenu.add(testShowText);

    JMenuItem showForm = new JMenuItem("RequestHandler Test");
    showForm.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String form = "<html><head><title>RequestHandler test</title></head>";
        form += "<body><h1>RequestHandler test</h1>";
        form += "<form action=\"http://www.google.com/\" method=\"post\">";
        form += "<input type=\"text\" name=\"searchFor\"/>";
        form += "<input type=\"submit\"/><br/>";
        form += "<input type=\"checkbox\" name=\"sendAsGet\"> Use GET instead of POST";
        form += "<p>This form tries to send the content of the text field as HTTP-POST request to http://www.google.com.</p>";
        form += "<h2>Testcase 1</h2>";
        form += "Try to enter the word <b>\"ignore\"</b> into the text field and press \"submit\".<br />";
        form += "The request will be rejected by the application.";
        form += "<p>See implementation of <u>tests.RequestHandler.onBeforeBrowse(CefBrowser, CefRequest, boolean)</u> for details</p>";
        form += "<h2>Testcase 2</h2>";
        form += "Due Google doesn't allow the POST method, the server replies with a 405 error.</br>";
        form +=
          "If you activate the checkbox \"Use GET instead of POST\", the application will change the POST request into a GET request.";
        form += "<p>See implementation of <u>tests.RequestHandler.onBeforeResourceLoad(CefBrowser, CefRequest)</u> for details</p>";
        form += "</form>";
        form += "</body></html>";
        browser_.loadURL(DataUri.create("text/html", form));
      }
    });
    testMenu.add(showForm);

    JMenuItem httpRequest = new JMenuItem("Manual HTTP request");
    httpRequest.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String searchFor = JOptionPane.showInputDialog(owner_, "Search on google:");
        if (searchFor != null && !searchFor.isEmpty()) {
          CefRequest myRequest = CefRequest.create();
          myRequest.setMethod("GET");
          myRequest.setURL("http://www.google.com/#q=" + searchFor);
          myRequest.setFirstPartyForCookies("http://www.google.com/#q=" + searchFor);
          browser_.loadRequest(myRequest);
        }
      }
    });
    testMenu.add(httpRequest);

    JMenuItem showInfo = new JMenuItem("Show Info");
    showInfo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String info = "<html><head><title>Browser status</title></head>";
        info += "<body><h1>Browser status</h1><table border=\"0\">";
        info += "<tr><td>CanGoBack</td><td>" + browser_.canGoBack() + "</td></tr>";
        info += "<tr><td>CanGoForward</td><td>" + browser_.canGoForward() + "</td></tr>";
        info += "<tr><td>IsLoading</td><td>" + browser_.isLoading() + "</td></tr>";
        info += "<tr><td>isPopup</td><td>" + browser_.isPopup() + "</td></tr>";
        info += "<tr><td>hasDocument</td><td>" + browser_.hasDocument() + "</td></tr>";
        info += "<tr><td>Url</td><td>" + browser_.getURL() + "</td></tr>";
        info += "<tr><td>Zoom-Level</td><td>" + browser_.getZoomLevel() + "</td></tr>";
        info += "</table></body></html>";
        String js = "var x=window.open(); x.document.open(); x.document.write('" + info
                    + "'); x.document.close();";
        browser_.executeJavaScript(js, "", 0);

        String jsFunc = "cef_query_" + MainFrame.queryCounter;
        String jsQuery = "window." + jsFunc + "({request: '" + jsFunc + "'});";
        browser_.executeJavaScript(jsQuery, "", 0);
      }
    });
    testMenu.add(showInfo);

    final JMenuItem showDevTools = new JMenuItem("Show DevTools");
    showDevTools.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DevToolsDialog devToolsDlg = new DevToolsDialog(owner_, "DEV Tools", browser_);
        devToolsDlg.addComponentListener(new ComponentAdapter() {
          @Override
          public void componentHidden(ComponentEvent e) {
            showDevTools.setEnabled(true);
          }
        });
        devToolsDlg.setVisible(true);
        showDevTools.setEnabled(false);
      }
    });
    testMenu.add(showDevTools);

    JMenu devToolsProtocolMenu = new JMenu("DevTools Protocol");
    JMenuItem autoDarkMode = devToolsProtocolMenu.add(new JCheckBoxMenuItem("Auto Dark Mode"));
    autoDarkMode.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // Toggle the auto dark mode override
        String params = String.format("{ \"enabled\": %s }", autoDarkMode.isSelected());
        executeDevToolsMethod("Emulation.setAutoDarkModeOverride", params);
      }
    });
    JMenuItem checkContrast = devToolsProtocolMenu.add(new JMenuItem("Check Contrast"));
    checkContrast.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // Check contrast, which usually triggers a series of Audits.issueAdded events
        executeDevToolsMethod("Audits.checkContrast");
      }
    });
    JMenuItem enableCSS = devToolsProtocolMenu.add(new JMenuItem("Enable CSS Agent"));
    enableCSS.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // Enable the CSS agent, which usually triggers a series of CSS.styleSheetAdded
        // events. We can only enable the CSS agent if the DOM agent is enabled first, so we
        // need to chain the two commands.
        executeDevToolsMethod("DOM.enable")
          .thenCompose(unused -> executeDevToolsMethod("CSS.enable"));
      }
    });
    testMenu.add(devToolsProtocolMenu);

    JMenuItem testURLRequest = new JMenuItem("URL Request");
    testURLRequest.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UrlRequestDialog dlg = new UrlRequestDialog(owner_, "URL Request Test");
        dlg.setVisible(true);
      }
    });
    testMenu.add(testURLRequest);

    JMenuItem reparent = new JMenuItem("Reparent");
    reparent.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final BrowserFrame newFrame = new BrowserFrame("New Window");
        newFrame.setLayout(new BorderLayout());
        final JButton reparentButton = new JButton("Reparent <");
        reparentButton.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (reparentPending_) return;
            reparentPending_ = true;

            if (reparentButton.getText().equals("Reparent <")) {
              owner_.removeBrowser(() -> {
                newFrame.add(browser_.getUIComponent(), BorderLayout.CENTER);
                newFrame.setBrowser(browser_);
                reparentButton.setText("Reparent >");
                reparentPending_ = false;
              });
            }
            else {
              newFrame.removeBrowser(new Runnable() {
                @Override
                public void run() {
                  JRootPane rootPane = (JRootPane)owner_.getComponent(0);
                  Container container = rootPane.getContentPane();
                  JPanel panel = (JPanel)container.getComponent(0);
                  panel.add(browser_.getUIComponent());
                  owner_.setBrowser(browser_);
                  owner_.revalidate();
                  reparentButton.setText("Reparent <");
                  reparentPending_ = false;
                }
              });
            }
          }
        });
        newFrame.add(reparentButton, BorderLayout.NORTH);
        newFrame.setSize(400, 400);
        newFrame.setVisible(true);
      }
    });
    testMenu.add(reparent);

    JMenuItem newwindow = new JMenuItem("New window");
    newwindow.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final MainFrame frame = new MainFrame(owner_.getCefApp());
        frame.setSize(800, 600);
        frame.setVisible(true);
      }
    });
    testMenu.add(newwindow);

    JMenuItem screenshotSync = new JMenuItem("Screenshot (on AWT thread, native res)");
    screenshotSync.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CompletableFuture<BufferedImage> shot = browser.createScreenshot(true);
        try {
          displayScreenshot(shot.get());
        }
        catch (InterruptedException | ExecutionException exc) {
          // cannot happen, future is already resolved in this case
        }
      }
    });
    screenshotSync.setEnabled(true);
    testMenu.add(screenshotSync);

    JMenuItem screenshotSyncScaled = new JMenuItem("Screenshot (on AWT thread, scaled)");
    screenshotSyncScaled.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CompletableFuture<BufferedImage> shot = browser.createScreenshot(false);
        try {
          displayScreenshot(shot.get());
        }
        catch (InterruptedException | ExecutionException exc) {
          // cannot happen, future is already resolved in this case
        }
      }
    });
    screenshotSyncScaled.setEnabled(true);
    testMenu.add(screenshotSyncScaled);

    JMenuItem screenshotAsync = new JMenuItem("Screenshot (from other thread, scaled)");
    screenshotAsync.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CompletableFuture<BufferedImage> shot = browser.createScreenshot(false);
        shot.thenAccept((image) -> {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              displayScreenshot(image);
            }
          });
        });
      }
    });
    screenshotAsync.setEnabled(true);
    testMenu.add(screenshotAsync);

    add(fileMenu);
    add(bookmarkMenu_);
    add(testMenu);
  }

  public void addBookmark(String name, String URL) {
    if (bookmarkMenu_ == null) return;

    // Test if the bookmark already exists. If yes, update URL
    Component[] entries = bookmarkMenu_.getMenuComponents();
    for (Component itemEntry : entries) {
      if (!(itemEntry instanceof JMenuItem)) continue;

      JMenuItem item = (JMenuItem)itemEntry;
      if (item.getText().equals(name)) {
        item.setActionCommand(URL);
        return;
      }
    }

    JMenuItem menuItem = new JMenuItem(name);
    menuItem.setActionCommand(URL);
    menuItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        browser_.loadURL(e.getActionCommand());
      }
    });
    bookmarkMenu_.add(menuItem);
    validate();
  }

  private void displayScreenshot(BufferedImage aScreenshot) {
    JFrame frame = new JFrame("Screenshot");
    ImageIcon image = new ImageIcon();
    image.setImage(aScreenshot);
    frame.setLayout(new FlowLayout());
    JLabel label = new JLabel(image);
    label.setPreferredSize(new Dimension(aScreenshot.getWidth(), aScreenshot.getHeight()));
    frame.add(label);
    frame.setVisible(true);
    frame.pack();
  }

  private CompletableFuture<String> executeDevToolsMethod(String methodName) {
    return executeDevToolsMethod(methodName, null);
  }

  private CompletableFuture<String> executeDevToolsMethod(
    String methodName, String paramsAsJson) {
    if (devToolsClient_ == null) {
      devToolsClient_ = browser_.getDevToolsClient();
      devToolsClient_.addEventListener(
        (method, json) -> {
          // System.out.println("CDP event " + method + ": " + json);
        });
    }

    return devToolsClient_.executeDevToolsMethod(methodName, paramsAsJson)
      .handle((error, json) -> {
        return null;
      });
  }

  public void addBookmarkSeparator() {
    bookmarkMenu_.addSeparator();
  }
}
