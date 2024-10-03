// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefCookie;
import org.cef.CefApp;
import org.cef.handler.CefAppStateHandler;
import org.cef.handler.CefLoadHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * @author tav
 */
final class JBCefBrowserDemo extends AnAction implements DumbAware {

  private static final String URL = "https://maps.google.com";
  private static final String myCookieManagerText = "Cookie Manager";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;

    if (!JBCefApp.isSupported()) {
      JBPopupFactory.getInstance().createComponentPopupBuilder(
          new JTextArea("Set the reg key to enable JCEF:\n\"ide.browser.jcef.enabled=true\""), null).
        setTitle("JCEF Web Browser Is not Supported").
        createPopup().
        showInCenterOf(activeFrame);
      return;
    }

    showBrowser(JBCefApp.isOffScreenRenderingModeEnabled());
  }

  private static void showBrowser(boolean isOffScreenRendering) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;

    Rectangle bounds = activeFrame.getGraphicsConfiguration().getBounds();

    final JFrame frame = new IdeFrameImpl();
    frame.setTitle("Web Browser" + (isOffScreenRendering ? " (OSR) " : " ") + "- JCEF");
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.setBounds(bounds.width / 4, bounds.height / 4, bounds.width / 2, bounds.height / 2);
    frame.setLayout(new BorderLayout());

    final JBCefBrowser myJBCefBrowser = JBCefBrowser.createBuilder()
      .setOffScreenRendering(isOffScreenRendering)
      .setUrl(URL)
      .setEnableOpenDevToolsMenuItem(true)
      .build();

    myJBCefBrowser.setErrorPage(new JBCefBrowserBase.ErrorPage() {
      @Override
      public @Nullable String create(@NotNull CefLoadHandler.ErrorCode errorCode,
                                     @NotNull String errorText,
                                     @NotNull String failedUrl)
      {
        return (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) ?
               null : JBCefBrowserBase.ErrorPage.DEFAULT.create(errorCode, errorText, failedUrl);
      }
    });
    myJBCefBrowser.setProperty(JBCefBrowser.Properties.FOCUS_ON_SHOW, Boolean.TRUE);


    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        Disposer.dispose(myJBCefBrowser);
      }
    });

    frame.add(myJBCefBrowser.getComponent(), BorderLayout.CENTER);

    final JTextField myUrlBar = new JTextField(URL);
    myUrlBar.addActionListener(event -> myJBCefBrowser.loadURL(myUrlBar.getText()));
    frame.add(myUrlBar, BorderLayout.NORTH);

    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));

    CefApp.getInstance().onInitialization(new CefAppStateHandler() {
      @Override
      public void stateHasChanged(CefApp.CefAppState state) {
        if (state == CefApp.CefAppState.INITIALIZED) {
          SwingUtilities.invokeLater(() -> {
            final CookieManagerDialog myCookieManagerDialog = new CookieManagerDialog(frame, myJBCefBrowser);
            myCookieManagerDialog.setVisible(false);
            final JButton myShowCookieManagerButton = new JButton(myCookieManagerText);
            myShowCookieManagerButton.addActionListener(new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                myCookieManagerDialog.setVisible(true);
                List<JBCefCookie> cookies = myJBCefBrowser.getJBCefCookieManager().getCookies();
                myCookieManagerDialog.update(cookies);
              }
            });
            controlPanel.add(myShowCookieManagerButton);
          });
        }
      }
    });

    frame.add(controlPanel, BorderLayout.SOUTH);

    JMenuBar menuBar = new JMenuBar();
    frame.setJMenuBar(menuBar);
    JMenu menu = new JMenu("Tools");
    menu.setMnemonic('t');
    menuBar.add(menu);
    JMenuItem menuItem = new JMenuItem("Load HTML with URL", 'h');
    menu.add(menuItem);
    menuItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyOkCancelDialog<JPanel> dialog = new MyOkCancelDialog<>(frame, "Load HTML with URL");
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        JTextField url = new JTextField("file://foo/bar");
        //noinspection NonAsciiCharacters
        JTextArea html = new JTextArea("<html>\n<body>\nСъешь Еще Этих Мягких Французских Булок &#129366;&#129366;\n</body>\n</html>");
        panel.add(url, BorderLayout.NORTH);
        panel.add(html, BorderLayout.CENTER);

        dialog.setComponent(panel);

        dialog.setOkAction(() -> {
          myUrlBar.setText(url.getText());
          SwingUtilities.invokeLater(() -> myJBCefBrowser.loadHTML(html.getText(), url.getText()));
        }, "Load");
        dialog.show();
      }
    });

    menuItem = new JMenuItem("Set background color", 'c');
    menu.add(menuItem);
    menuItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyOkCancelDialog<JTextField> dialog = new MyOkCancelDialog<>(frame, "Background Color");
        JTextField color = dialog.setComponent(new JTextField("lightgreen"));
        dialog.setOkAction(() -> myJBCefBrowser.setPageBackgroundColor(color.getText()), "Apply");
        dialog.show();
      }
    });

    final JMenuItem menuItemFocus = new JMenuItem("Set focus on navigation", 'f');
    menu.add(menuItemFocus);
    menuItemFocus.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean value = Boolean.TRUE.equals(myJBCefBrowser.getProperty(JBCefBrowser.Properties.FOCUS_ON_NAVIGATION));
        myJBCefBrowser.setProperty(JBCefBrowser.Properties.FOCUS_ON_NAVIGATION, !value);
        menuItemFocus.setText(value ? "Set focus on navigation" : "Unset focus on navigation");
      }
    });

    final JMenuItem menuItemContext = new JMenuItem("Disable context menu", 'm');
    menu.add(menuItemContext);
    menuItemContext.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean value = Boolean.TRUE.equals(myJBCefBrowser.getProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU));
        myJBCefBrowser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, !value);
        menuItemContext.setText(value ? "Disable context menu" : "Enable context menu");
      }
    });

    if (JBCefApp.isOffScreenRenderingModeEnabled()) {
      JMenuItem menuItemWindowedMode = new JMenuItem("Create jcef browser in Windowed-mode");
      menu.add(menuItemWindowedMode);
      menuItemWindowedMode.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          showBrowser(false);
        }
      });
    }
    frame.setVisible(true);
  }
}

final class MyOkCancelDialog<T extends JComponent> extends DialogWrapper {
  T myComp;
  Runnable myOkAction;

  MyOkCancelDialog(@NotNull JFrame owner, @NotNull String title)  {
    super(null, owner, true, IdeModalityType.IDE);

    setTitle(title);
  }

  public T setComponent(@NotNull T comp) {
    return myComp = comp;
  }

  public void setOkAction(@NotNull Runnable okAction, @NotNull String buttonText) {
    myOkAction = okAction;
    setOKButtonText(buttonText);
  }

  @Override
  protected @NotNull Action getOKAction() {
    return new DialogWrapper.OkAction() {
      @Override
      protected void doAction(ActionEvent e) {
        myOkAction.run();
        super.doAction(e);
      }
    };
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myComp;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myComp;
  }

  @Override
  public void show() {
    init();
    super.show();
  }
}
