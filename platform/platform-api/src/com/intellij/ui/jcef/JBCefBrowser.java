// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import org.cef.browser.CefBrowser;
import org.cef.handler.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A wrapper over {@link CefBrowser}.
 * <p>
 * Use {@link #getComponent()} as the browser's UI component.
 * Use {@link #loadURL(String)} or {@link #loadHTML(String)} for loading.
 *
 * @author tav
 */
@ApiStatus.Experimental
public class JBCefBrowser implements JBCefDisposable {
  @NotNull private final JBCefClient myCefClient;
  @NotNull private final MyComponent myComponent;
  @NotNull private final CefBrowser myCefBrowser;
  @NotNull private final CefFocusHandler myCefFocusHandler;
  @NotNull private final CefLifeSpanHandler myLifeSpanHandler;
  @NotNull private final DisposeHelper myDisposeHelper = new DisposeHelper();

  private final boolean myIsDefaultClient;
  private volatile boolean myIsCefBrowserCreated;
  @Nullable private volatile DeferLoader myDeferLoader;

  // CEF demands async loading
  private enum DeferLoader {
    URL {
      @Override
      public void load(@NotNull CefBrowser browser) {
        EventQueue.invokeLater(() -> browser.loadURL(url));
      }

      @NotNull
      @Override
      public DeferLoader with(@NotNull String value) {
        this.url = value;
        return this;
      }
    },
    HTML {
      @Override
      public void load(@NotNull CefBrowser browser) {
        EventQueue.invokeLater(() -> browser.loadString(html, "about:blank"));
      }

      @NotNull
      @Override
      public DeferLoader with(@NotNull String value) {
        this.html = value;
        return this;
      }
    };

    @NotNull protected String html = "";
    @NotNull protected String url = "about:blank";

    @NotNull
    public abstract DeferLoader with(@NotNull String value);

    public abstract void load(@NotNull CefBrowser browser);
  }

  /**
   * Creates a browser with the provided {@code JBCefClient} and initial URL. The client's lifecycle is the responsibility of the caller.
   */
  public JBCefBrowser(@NotNull JBCefClient client, @Nullable String url) {
    this(client, false, url);
  }

  private JBCefBrowser(@NotNull JBCefClient client, boolean isDefaultClient, @Nullable String url) {
    if (client.isDisposed()) {
      throw new IllegalArgumentException("JBCefClient is disposed");
    }
    myCefClient = client;
    myIsDefaultClient = isDefaultClient;

    myComponent = new MyComponent(new BorderLayout());
    myComponent.setBackground(JBColor.background());

    myCefBrowser = myCefClient.getCefClient().createBrowser(url != null ? url : "about:blank", false, false);
    myComponent.add(myCefBrowser.getUIComponent(), BorderLayout.CENTER);

    myCefClient.
      addLifeSpanHandler(myLifeSpanHandler = new CefLifeSpanHandlerAdapter() {
      @Override
      public void onAfterCreated(CefBrowser browser) {
        myIsCefBrowserCreated = true;
        DeferLoader loader = myDeferLoader;
        if (loader != null) {
          loader.load(browser);
          myDeferLoader = null;
        }
      }
    }, myCefBrowser).
      addFocusHandler(myCefFocusHandler = new CefFocusHandlerAdapter() {
      @Override
      public boolean onSetFocus(CefBrowser browser, FocusSource source) {
        if (source == FocusSource.FOCUS_SOURCE_NAVIGATION) return true;
        // Workaround: JCEF doesn't change current focus on the client side.
        // Clear the focus manually and this will report focus loss to the client
        // and will let focus return to the client on mouse click.
        // tav [todo]: the opposite is inadequate
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        return false;
      }
    }, myCefBrowser);
  }

  public void loadURL(@NotNull String url) {
    if (myIsCefBrowserCreated) {
      myCefBrowser.loadURL(url);
    }
    else {
      myDeferLoader = DeferLoader.URL.with(url);
    }
  }

  public void loadHTML(@NotNull String html) {
    if (myIsCefBrowserCreated) {
      myCefBrowser.loadString(html, "about:blank");
    }
    else {
      myDeferLoader = DeferLoader.HTML.with(html);
    }
  }

  /**
   * Creates a browser with default {@link JBCefClient}. The default client is disposed with this browser and may not be used with other browsers.
   */
  @SuppressWarnings("unused")
  public JBCefBrowser() {
    this(JBCefApp.getInstance().createClient(), true, null);
  }

  /**
   * @see #JBCefBrowser()
   * @param url initial url
   */
  @SuppressWarnings("unused")
  public JBCefBrowser(@NotNull String url) {
    this(JBCefApp.getInstance().createClient(), true, url);
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  public CefBrowser getCefBrowser() {
    return myCefBrowser;
  }

  @NotNull
  public JBCefClient getJBCefClient() {
    return myCefClient;
  }

  @Override
  public void dispose() {
    myDisposeHelper.dispose(() -> {
      myCefClient.removeFocusHandler(myCefFocusHandler, myCefBrowser);
      myCefClient.removeLifeSpanHandler(myLifeSpanHandler, myCefBrowser);
      myCefBrowser.stopLoad();
      myCefBrowser.close(false);
      if (myIsDefaultClient) {
        Disposer.dispose(myCefClient);
      }
    });
  }

  @Override
  public boolean isDisposed() {
    return myDisposeHelper.isDisposed();
  }

  @SuppressWarnings("unused")
  @Contract("null->null; !null->!null")
  protected static JBCefBrowser getJBCefBrowser(CefBrowser browser) {
    if (browser == null) return null;
    return ((MyComponent)browser.getUIComponent().getParent()).getJBCefBrowser();
  }

  private class MyComponent extends JPanel {
    MyComponent(BorderLayout layout) {
      super(layout);
    }

    JBCefBrowser getJBCefBrowser() {
      return JBCefBrowser.this;
    }
  }
}
