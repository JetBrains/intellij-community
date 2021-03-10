// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.credentialStore.Credentials;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefNativeAdapter;
import org.cef.handler.*;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import static com.intellij.ui.scale.ScaleType.OBJ_SCALE;
import static com.intellij.ui.scale.ScaleType.SYS_SCALE;

/**
 * Base class for windowed and offscreen browsers.
 */
@SuppressWarnings("unused")
public abstract class JBCefBrowserBase implements JBCefDisposable {
  /**
   * @see #setProperty(String, Object)
   */
  public static class Properties {
    /**
     * Prevents the browser from providing credentials via the
     * {@link CefRequestHandler#getAuthCredentials(CefBrowser, String, boolean, String, int, String, String, CefAuthCallback)} callback.
     * <p></p>
     * Accepts {@link Boolean} values. Use the property to handle the callback on your own.
     */
    public static final @NotNull String NO_DEFAULT_AUTH_CREDENTIALS = "JBCefBrowserBase.noDefaultAuthCredentials";

    static {
      PropertiesHelper.putType(NO_DEFAULT_AUTH_CREDENTIALS, Boolean.class);
    }
  }

  @NotNull protected static final String BLANK_URI = "about:blank";
  @NotNull private static final Icon ERROR_PAGE_ICON = AllIcons.General.ErrorDialog;

  @SuppressWarnings("SpellCheckingInspection")
  protected static final String JBCEFBROWSER_INSTANCE_PROP = "JBCefBrowser.instance";
  @NotNull private final DisposeHelper myDisposeHelper = new DisposeHelper();
  @Nullable private volatile LoadDeferrer myLoadDeferrer;
  @NotNull private String myLastRequestedUrl = "";
  @NotNull private final Object myLastRequestedUrlLock = new Object();
  @Nullable private volatile ErrorPage myErrorPage;
  @NotNull protected final PropertiesHelper myPropertiesHelper = new PropertiesHelper();

  private static final LazyInitializer.NotNullValue<String> ERROR_PAGE_READER =
    new LazyInitializer.NotNullValue<>() {
      @Override
      public @NotNull String initialize() {
        try {
          return new String(FileUtil.loadBytes(Objects.requireNonNull(
            JBCefApp.class.getResourceAsStream("resources/load_error.html"))), StandardCharsets.UTF_8);
        }
        catch (IOException | NullPointerException e) {
          Logger.getInstance(JBCefBrowser.class).error("couldn't find load_error.html", e);
        }
        return "";
      }
    };

  private static final LazyInitializer.NotNullValue<ScaleContext.Cache<String>> BASE64_ERROR_PAGE_ICON =
    new LazyInitializer.NotNullValue<>() {
      @Override
      public @NotNull ScaleContext.Cache<String> initialize() {
        return new ScaleContext.Cache<>((ctx) -> {
          try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = IconUtil.toBufferedImage(IconUtil.scale(ERROR_PAGE_ICON, ctx), false);
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
          }
          catch (IOException ex) {
            Logger.getInstance(JBCefBrowser.class).error("couldn't write an error image", ex);
          }
          return "";
        });
      }
    };

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
  @Nullable private final CefLifeSpanHandler myLifeSpanHandler;
  @Nullable private final CefLoadHandler myLoadHandler;
  @Nullable private final CefRequestHandler myRequestHandler;
  private final ReentrantLock myCookieManagerLock = new ReentrantLock();
  protected volatile boolean myIsCefBrowserCreated;
  @Nullable private volatile JBCefCookieManager myJBCefCookieManager;
  private final boolean myIsDefaultClient;
  @Nullable private volatile String myCssBgColor;

  JBCefBrowserBase(@NotNull JBCefClient cefClient, @NotNull CefBrowser cefBrowser, boolean isNewBrowserCreated, boolean isDefaultClient) {
    myCefClient = cefClient;
    myCefBrowser = cefBrowser;
    myIsDefaultClient = isDefaultClient;

    if (isNewBrowserCreated) {
      cefClient.addLifeSpanHandler(myLifeSpanHandler = new CefLifeSpanHandlerAdapter() {
        @Override
        public void onAfterCreated(CefBrowser browser) {
          myIsCefBrowserCreated = true;
          LoadDeferrer loader = myLoadDeferrer;
          if (loader != null) {
            loader.load();
            myLoadDeferrer = null;
          }
        }
      }, getCefBrowser());

      // check if the native browser has already been created (no onAfterCreated will be called then)
      if (cefBrowser instanceof CefNativeAdapter && ((CefNativeAdapter)cefBrowser).getNativeRef("CefBrowser") != 0) {
        myIsCefBrowserCreated = true;
      }

      cefClient.addLoadHandler(myLoadHandler = new CefLoadHandlerAdapter() {
        @Override
        public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
          setPageBackgroundColor();
        }
        @Override
        public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
          // do not show error page if another URL has already been requested to load
          ErrorPage errorPage = myErrorPage;
          String lastRequestedUrl = getLastRequestedUrl();
          if (errorPage != null && lastRequestedUrl.equals(failedUrl)) {
            String html = errorPage.create(errorCode, errorText, failedUrl);
            if (html != null) UIUtil.invokeLaterIfNeeded(() -> compareLastRequestedUrlAndPerform(failedUrl, () -> loadHTML(html)));
          }
        }
      }, getCefBrowser());

      cefClient.addRequestHandler(myRequestHandler = new CefRequestHandlerAdapter() {
        @Override
        public boolean onBeforeBrowse(CefBrowser browser,
                                      CefFrame frame,
                                      CefRequest request,
                                      boolean user_gesture,
                                      boolean is_redirect)
        {
          setLastRequestedUrl(ObjectUtils.notNull(request.getURL(), ""));
          return super.onBeforeBrowse(browser, frame, request, user_gesture, is_redirect);
        }
        @Override
        public boolean getAuthCredentials(CefBrowser browser,
                                          String origin_url,
                                          boolean isProxy,
                                          String host,
                                          int port,
                                          String realm,
                                          String scheme, CefAuthCallback callback)
        {
          if (isProxy && !myPropertiesHelper.isTrue(Properties.NO_DEFAULT_AUTH_CREDENTIALS)) {
            Credentials credentials = JBCefProxyAuthenticator.getCredentials(JBCefBrowserBase.this, host, port);
            if (credentials != null) {
              callback.Continue(credentials.getUserName(), credentials.getPasswordAsString());
              return true;
            }
            Logger.getInstance(JBCefBrowserBase.class).error("missing credentials to sign in to proxy");
          }
          return super.getAuthCredentials(browser, origin_url, isProxy, host, port, realm, scheme, callback);
        }
      }, getCefBrowser());
    }
    else {
      myLifeSpanHandler = null;
      myLoadHandler = null;
      myRequestHandler = null;
      myIsCefBrowserCreated = true; // if the browser comes from the outer - consider its native browser created
    }
  }

  /**
   * Loads URL.
   */
  public final void loadURL(@NotNull String url) {
    if (myIsCefBrowserCreated) {
      loadUrlImpl(url);
    }
    else {
      myLoadDeferrer = new LoadDeferrer(null, url);
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
      loadHtmlImpl(html, url);
    }
    else {
      myLoadDeferrer = new LoadDeferrer(html, url);
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
    dispose(null);
  }

  protected void dispose(@Nullable Runnable subDisposer) {
    myDisposeHelper.dispose(() -> {
      if (subDisposer != null) subDisposer.run();

      if (myLifeSpanHandler != null) getJBCefClient().removeLifeSpanHandler(myLifeSpanHandler, getCefBrowser());
      if (myLoadHandler != null) getJBCefClient().removeLoadHandler(myLoadHandler, getCefBrowser());
      if (myRequestHandler != null) getJBCefClient().removeRequestHandler(myRequestHandler, getCefBrowser());

      myCefBrowser.stopLoad();
      myCefBrowser.close(true);

      if (myIsDefaultClient) Disposer.dispose(myCefClient);
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

  /**
   * Sets (overrides) background color in the html page.
   * <p></p>
   * The color is set for the currently displayed page and all the subsequently loaded pages.
   *
   * @see <a href="https://www.w3schools.com/cssref/css_colors_legal.asp">css color format</a>
   * @param cssColor the color in CSS format
   */
  public void setPageBackgroundColor(@NotNull String cssColor) {
    myCssBgColor = cssColor;
    setPageBackgroundColor();
  }

  private void setPageBackgroundColor() {
    if (myCssBgColor != null) {
      getCefBrowser().executeJavaScript("document.body.style.backgroundColor = \"" + myCssBgColor + "\";", BLANK_URI, 0);
    }
  }

  private void loadHtmlImpl(@NotNull String html, @NotNull String url) {
    loadUrlImpl(JBCefFileSchemeHandlerFactory.registerLoadHTMLRequest(getCefBrowser(), html, url));
  }

  private void loadUrlImpl(@NotNull String url) {
    setLastRequestedUrl(""); // will be set to a correct value in onBeforeBrowse()
    getCefBrowser().loadURL(url);
  }

  private String getLastRequestedUrl() {
    synchronized (myLastRequestedUrlLock) {
      return myLastRequestedUrl;
    }
  }

  private void setLastRequestedUrl(@NotNull String url) {
    synchronized (myLastRequestedUrlLock) {
      myLastRequestedUrl = url;
    }
  }

  private void compareLastRequestedUrlAndPerform(@NotNull String url, @NotNull Runnable action) {
    synchronized (myLastRequestedUrlLock) {
      if (myLastRequestedUrl.equals(url)) action.run();
    }
  }

  /**
   * Used to create an error page.
   */
  public interface ErrorPage {
    /**
     * Default error page.
     */
    ErrorPage DEFAULT = new ErrorPage() {
      @Override
      public @NotNull String create(CefLoadHandler.@NotNull ErrorCode errorCode,
                                    @NotNull String errorText,
                                    @NotNull String failedUrl)
      {
        int fontSize = (int)(EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize() * 1.1);
        int headerFontSize = fontSize + JBUIScale.scale(3);
        int headerPaddingTop = headerFontSize / 5;
        int lineHeight = headerFontSize * 2;
        int iconPaddingRight = JBUIScale.scale(12);
        Color bgColor = JBColor.background();
        String bgWebColor = String.format("#%02x%02x%02x", bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue());
        Color fgColor = JBColor.foreground();
        String fgWebColor = String.format("#%02x%02x%02x", fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue());

        String html = ERROR_PAGE_READER.get();
        html = html.replace("${lineHeight}", String.valueOf(lineHeight));
        html = html.replace("${iconPaddingRight}", String.valueOf(iconPaddingRight));
        html = html.replace("${fontSize}", String.valueOf(fontSize));
        html = html.replace("${headerFontSize}", String.valueOf(headerFontSize));
        html = html.replace("${headerPaddingTop}", String.valueOf(headerPaddingTop));
        html = html.replace("${bgWebColor}", bgWebColor);
        html = html.replace("${fgWebColor}", fgWebColor);
        html = html.replace("${errorText}", errorText);
        html = html.replace("${failedUrl}", failedUrl);

        ScaleContext ctx = ScaleContext.create();
        ctx.update(OBJ_SCALE.of(1.2 * headerFontSize / (float)ERROR_PAGE_ICON.getIconHeight()));
        // Reset sys scale to prevent raster downscaling on passing the image to jcef.
        // Overriding is used to prevent scale change during further intermediate context transformations.
        ctx.overrideScale(SYS_SCALE.of(1.0));

        html = html.replace("${base64Image}", ObjectUtils.notNull(BASE64_ERROR_PAGE_ICON.get().getOrProvide(ctx), ""));

        return html;
      }
    };

    /**
     * Returns an error page html.
     * <p></p>
     * To prevent showing the error page (e.g. filter out {@link CefLoadHandler.ErrorCode#ERR_ABORTED}) just return {@code null}.
     * To fallback to default error page return {@link ErrorPage#DEFAULT#create(CefLoadHandler.ErrorCode, String, String)}.
     */
    @Nullable
    String create(@NotNull @SuppressWarnings("unused") CefLoadHandler.ErrorCode errorCode, @NotNull String errorText, @NotNull String failedUrl);
  }

  /**
   * Sets the error page to display in the browser on load error.
   * <p></p>
   * By default no error page is displayed. To enable displaying default error page pass {@link ErrorPage#DEFAULT}.
   * Passing {@code null} prevents the browser from displaying an error page.
   *
   * @param errorPage the error page producer, or null
   */
  public void setErrorPage(@Nullable ErrorPage errorPage) {
    myErrorPage = errorPage;
  }

  /**
   * Supports {@link Properties}.
   *
   * @throws IllegalArgumentException if the value has wrong type or format
   */
  public void setProperty(@NotNull String name, @Nullable Object value) {
    myPropertiesHelper.setProperty(name, value);
  }

  /**
   * @see #setProperty(String, Object)
   */
  @Nullable
  public Object getProperty(@NotNull String name) {
    return myPropertiesHelper.getProperty(name);
  }

  /**
   * @see #setProperty(String, Object)
   */
  void addPropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertiesHelper.addPropertyChangeListener(name, listener);
  }

  /**
   * @see #setProperty(String, Object)
   */
  void removePropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertiesHelper.removePropertyChangeListener(name, listener);
  }

  private final class LoadDeferrer {
    @Nullable private final String myHtml;
    @NotNull private final String myUrl;

    private LoadDeferrer(@Nullable String html, @NotNull String url) {
      myHtml = html;
      myUrl = url;
    }

    public void load() {
      // JCEF demands async loading.
      SwingUtilities.invokeLater(
        myHtml == null ?
        () -> loadUrlImpl(myUrl) :
        () -> loadHtmlImpl(myHtml, myUrl));
    }
  }
}