// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.diagnostic.Logger;
import org.cef.callback.CefCookieVisitor;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A wrapper over {@link CefCookieManager}.
 * <p>
 * Use {@link #getCookies()} for retrieving cookies. Note: getting cookies goes asynchronously due to CEF design limitations.
 *
 * @author hatari
 */
@ApiStatus.Experimental
public class JBCefCookieManager {
  public final Logger LOG = Logger.getInstance(JBCefCookieManager.class);

  @NotNull private final CefCookieManager myCefCookieManager;

  public JBCefCookieManager(@NotNull CefCookieManager cefCookieManager) {
    myCefCookieManager = cefCookieManager;
  }

  @SuppressWarnings("unused")
  @NotNull
  public CefCookieManager getCefCookieManager() {
    return myCefCookieManager;
  }

  /**
   * @see JBCefCookieManager#getCookies(String, boolean)
   */
  public List<CefCookie> getCookies() {
    return getCookies(null);
  }

  /**
   * @see JBCefCookieManager#getCookies(String, boolean)
   */
  public List<CefCookie> getCookies(@Nullable String url) {
    return getCookies(url, false);
  }

  /**
   * Method for getting cookies. Underlying native method is asynchronous.
   * This method is executed with synchronization and can take up to 200 ms.
   * @param url filter by the given url scheme, host, domain and path
   * @param includeHttpOnly include only true HTTP-only cookies
   * @return list of {@link CefCookie}
   */
  public List<CefCookie> getCookies(@Nullable String url, boolean includeHttpOnly) {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final JBCookieVisitor cookieVisitor = new JBCookieVisitor(countDownLatch);

    if (url != null) {
      myCefCookieManager.visitUrlCookies(url, includeHttpOnly, cookieVisitor);
    }
    else {
      myCefCookieManager.visitAllCookies(cookieVisitor);
    }

    try {
      countDownLatch.await(200, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e) {
      LOG.error("Cookie visiting took more than 200 ms");
    }
    return cookieVisitor.getCookies();
  }

  private static class JBCookieVisitor implements CefCookieVisitor {
    final List<CefCookie> myCefCookies = new ArrayList<>();
    final CountDownLatch myCountDownLatch;

    JBCookieVisitor(CountDownLatch countDownLatch){
      myCountDownLatch = countDownLatch;
    }

    @Override
    public boolean visit(CefCookie cookie, int count, int total, BoolRef delete) {
      myCefCookies.add(cookie);
      if (count == total -1) {
        // last element
        myCountDownLatch.countDown();
      }
      return true;
    }

    public List<CefCookie> getCookies() {
      return myCefCookies;
    }
  }
}
