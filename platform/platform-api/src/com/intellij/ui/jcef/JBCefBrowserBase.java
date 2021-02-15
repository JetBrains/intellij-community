// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefLifeSpanHandler;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base class for windowed and offscreen browsers.
 */
public abstract class JBCefBrowserBase implements JBCefDisposable {

  protected static final String BLANK_URI = "about:blank";
  @SuppressWarnings("SpellCheckingInspection")
  protected static final String JBCEFBROWSER_INSTANCE_PROP = "JBCefBrowser.instance";
  @NotNull private final DisposeHelper myDisposeHelper = new DisposeHelper();
  @Nullable private volatile LoadDeferrer myLoadDeferrer;

  /**
   * According to
   * <a href="https://github.com/chromium/chromium/blob/55f44515cd0b9e7739b434d1c62f4b7e321cd530/third_party/blink/public/web/web_view.h#L191">SetZoomLevel</a>
   * docs, there is a geometric progression that starts with 0.0 and 1.2 common ratio.
   * Following functions provide API familiar to developers:
   *
   * @see #setZoomLevel(double)
   * @see #getZoomLevel()
   */
  private static final double ZOOM_COMMON_RATIO = 1.2;
  private static final double LOG_ZOOM = Math.log(ZOOM_COMMON_RATIO);
  @NotNull protected final JBCefClient myCefClient;
  @NotNull protected final CefBrowser myCefBrowser;
  @Nullable protected final CefLifeSpanHandler myLifeSpanHandler;
  private final ReentrantLock myCookieManagerLock = new ReentrantLock();
  protected volatile boolean myIsCefBrowserCreated;
  @Nullable private volatile JBCefCookieManager myJBCefCookieManager;

  JBCefBrowserBase(@NotNull JBCefClient cefClient, @NotNull CefBrowser cefBrowser, boolean newBrowserCreated) {
    myCefClient = cefClient;
    myCefBrowser = cefBrowser;

    if (newBrowserCreated) {
      cefClient.addLifeSpanHandler(myLifeSpanHandler = new CefLifeSpanHandlerAdapter() {
        @Override
        public void onAfterCreated(CefBrowser browser) {
          myIsCefBrowserCreated = true;
          LoadDeferrer loader = myLoadDeferrer;
          if (loader != null) {
            loader.load(browser);
            myLoadDeferrer = null;
          }
        }
      }, getCefBrowser());
    }
    else {
      myLifeSpanHandler = null;
    }
  }

  /**
   * Loads URL.
   */
  public final void loadURL(@NotNull String url) {
    if (myIsCefBrowserCreated) {
      myCefBrowser.loadURL(url);
    }
    else {
      myLoadDeferrer = JBCefBrowser.LoadDeferrer.urlDeferrer(url);
    }
  }

  /**
   * Loads html content.
   *
   * @param html content to load
   * @param url  a dummy URL that may affect restriction policy applied to the content
   */
  public final void loadHTML(@NotNull String html, @NotNull String url) {
    if (myIsCefBrowserCreated) {
      loadString(myCefBrowser, html, url);
    }
    else {
      myLoadDeferrer = JBCefBrowser.LoadDeferrer.htmlDeferrer(html, url);
    }
  }

  /**
   * Loads html content.
   */
  public final void loadHTML(@NotNull String html) {
    loadHTML(html, BLANK_URI);
  }

  @NotNull
  public final CefBrowser getCefBrowser() {
    return myCefBrowser;
  }

  /**
   * @param zoomLevel 1.0 is 100%.
   * @see #ZOOM_COMMON_RATIO
   */
  public final void setZoomLevel(double zoomLevel) {
    myCefBrowser.setZoomLevel(Math.log(zoomLevel) / LOG_ZOOM);
  }

  /**
   * @return 1.0 is 100%
   * @see #ZOOM_COMMON_RATIO
   */
  public final double getZoomLevel() {
    return Math.pow(ZOOM_COMMON_RATIO, myCefBrowser.getZoomLevel());
  }

  @NotNull
  public final JBCefClient getJBCefClient() {
    return myCefClient;
  }

  @NotNull
  public final JBCefCookieManager getJBCefCookieManager() {
    myCookieManagerLock.lock();
    try {
      if (myJBCefCookieManager == null) {
        myJBCefCookieManager = new JBCefCookieManager();
      }
      return Objects.requireNonNull(myJBCefCookieManager);
    }
    finally {
      myCookieManagerLock.unlock();
    }
  }

  @SuppressWarnings("unused")
  public final void setJBCefCookieManager(@NotNull JBCefCookieManager jBCefCookieManager) {
    myCookieManagerLock.lock();
    try {
      myJBCefCookieManager = jBCefCookieManager;
    }
    finally {
      myCookieManagerLock.unlock();
    }
  }

  final boolean isCefBrowserCreated() {
    return myIsCefBrowserCreated;
  }

  @Override
  public void dispose() {
    myDisposeHelper.dispose(() -> {
      if (myLifeSpanHandler != null) getJBCefClient().removeLifeSpanHandler(myLifeSpanHandler, getCefBrowser());
    });
  }

  @Override
  public final boolean isDisposed() {
    return myDisposeHelper.isDisposed();
  }

  /**
   * Returns {@code JBCefBrowser} instance associated with this {@code CefBrowser}.
   */
  @Nullable
  public static JBCefBrowser getJBCefBrowser(@NotNull CefBrowser browser) {
    Component uiComp = browser.getUIComponent();
    if (uiComp != null) {
      Component parentComp = uiComp.getParent();
      if (parentComp instanceof JComponent) {
        return (JBCefBrowser)((JComponent)parentComp).getClientProperty(JBCEFBROWSER_INSTANCE_PROP);
      }
    }
    return null;
  }

  private static void loadString(CefBrowser cefBrowser, String html, String url) {
    url = JBCefFileSchemeHandlerFactory.registerLoadHTMLRequest(cefBrowser, html, url);
    cefBrowser.loadURL(url);
  }

  protected static final class LoadDeferrer {
    @Nullable private final String myHtml;
    @NotNull private final String myUrl;

    private LoadDeferrer(@Nullable String html, @NotNull String url) {
      myHtml = html;
      myUrl = url;
    }

    @NotNull
    public static LoadDeferrer urlDeferrer(String url) {
      return new LoadDeferrer(null, url);
    }

    @NotNull
    public static LoadDeferrer htmlDeferrer(String html, String url) {
      return new LoadDeferrer(html, url);
    }


    public void load(@NotNull CefBrowser browser) {
      // JCEF demands async loading.
      SwingUtilities.invokeLater(
        myHtml == null ?
        () -> browser.loadURL(myUrl) :
        () -> loadString(browser, myHtml, myUrl));
    }
  }
}