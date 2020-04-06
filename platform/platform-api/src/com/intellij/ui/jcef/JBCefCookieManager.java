// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.cef.callback.CefCookieVisitor;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import static com.intellij.openapi.util.Clock.getTime;

/**
 * A wrapper over {@link CefCookieManager}. Most of underlying native methods are asynchronous.
 * <p>
 * Use {@link #getCookies()} and others for retrieving cookies synchronously.
 * <p>
 * Use {@link #setCookie(String, CefCookie, boolean)} and others for setting cookie. Can be used both with synchronization or without one.
 * <p>
 * Use {@link #deleteCookies(boolean)} and others for deleting cookies. Can be used both with synchronization or without one.
 *
 * @author Aleksey.Rostovskiy
 */
@ApiStatus.Experimental
public class JBCefCookieManager {
  private static final int DEFAULT_TIMEOUT_FOR_SYNCHRONOUS_FUNCTION = 200;
  private static final Logger LOG = Logger.getInstance(JBCefCookieManager.class);

  private final ReentrantLock myLock = new ReentrantLock();
  private final ExecutorService myExecutorService = AppExecutorUtil.getAppScheduledExecutorService();

  @NotNull private final CefCookieManager myCefCookieManager;

  public JBCefCookieManager() {
    this(CefCookieManager.getGlobalManager());
  }

  @SuppressWarnings("unused")
  public JBCefCookieManager(@NotNull String path, boolean persistSessionCookies) {
    this(CefCookieManager.createManager(path, persistSessionCookies));
  }

  private JBCefCookieManager(@NotNull CefCookieManager cefCookieManager) {
    myCefCookieManager = cefCookieManager;
  }

  @SuppressWarnings("unused")
  @NotNull
  public CefCookieManager getCefCookieManager() {
    return myCefCookieManager;
  }

  /**
   * @see JBCefCookieManager#getCookies(String, Boolean, Integer)
   */
  public @Nullable List<CefCookie> getCookies() {
    return getCookies(null, false, null);
  }

  /**
   * TODO[hatari] Doesn't work now, waiting for JCEF library update <p/>
   *
   * @see JBCefCookieManager#getCookies(String, Boolean, Integer)
   */
  public @Nullable List<CefCookie> getCookies(@NotNull String url) {
    return getCookies(url, false, null);
  }

  /**
   * Gets cookies. Underlying native method is asynchronous.
   * This method is executed with synchronization and can take up to `maxTimeToWait` ms.
   *
   * @param url             filter by the given url scheme, host, domain and path.
   * @param includeHttpOnly include only true HTTP-only cookies.
   * @param maxTimeToWait   time to wait getting cookies in ms, default value is
   *                        {@link JBCefCookieManager#DEFAULT_TIMEOUT_FOR_SYNCHRONOUS_FUNCTION}.
   * @return list of {@link CefCookie} or null if cookies cannot be accessed
   */
  public @Nullable List<CefCookie> getCookies(@Nullable String url, @Nullable Boolean includeHttpOnly, @Nullable Integer maxTimeToWait) {
    long startTime = getTime();

    boolean httpOnly = includeHttpOnly != null ? includeHttpOnly : false;
    int timeout = maxTimeToWait != null ? maxTimeToWait : DEFAULT_TIMEOUT_FOR_SYNCHRONOUS_FUNCTION;

    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final JBCookieVisitor cookieVisitor = new JBCookieVisitor(countDownLatch);

    boolean result;
    if (url != null) {
      // TODO[hatari] Doesn't work now, waiting for JCEF library update <p/>
      result = myCefCookieManager.visitUrlCookies(url, httpOnly, cookieVisitor);
    }
    else {
      result = myCefCookieManager.visitAllCookies(cookieVisitor);
    }

    if (!result) {
      LOG.debug("Cookies cannot be accessed");
      countDownLatch.countDown();
      return null;
    }

    try {
      result = countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException e) {
      LOG.error("Cookie visiting is interrupted");
    }

    long timeSpent = getTime() - startTime;
    if (!result) {
      LOG.debug("Timeout for cookie visiting is reached, " + timeSpent + " ms time spent");
    }
    else {
      LOG.debug("Cookie getting took " + timeSpent + " ms");
    }

    return cookieVisitor.getCookies();
  }

  /**
   * @see JBCefCookieManager#setCookie(String, CefCookie, Integer)
   */
  public boolean setCookie(@NotNull String url, @NotNull CefCookie cefCookie, boolean doSync) {
    if (doSync) {
      return setCookie(url, cefCookie, null);
    }
    else {
      return myCefCookieManager.setCookie(url, cefCookie);
    }
  }

  /**
   * TODO[hatari] Doesn't work now, waiting for JCEF library update <p/>
   * <p>
   * Sets a cookie given a valid URL and explicit user-provided cookie attributes.
   * Underlying native method {@link CefCookieManager#setCookie(String, CefCookie)} is asynchronous.
   * This method is synchronous and will wait up to `maxTimeToWait` ms.
   *
   * @param maxTimeToWait time to wait setting cookie in ms, default value is
   *                      {@link JBCefCookieManager#DEFAULT_TIMEOUT_FOR_SYNCHRONOUS_FUNCTION} ms.
   * @return true if setting the cookie was successful.
   */
  public boolean setCookie(@NotNull String url, @NotNull CefCookie cefCookie, @Nullable Integer maxTimeToWait) {
    int timeout = maxTimeToWait != null ? maxTimeToWait : DEFAULT_TIMEOUT_FOR_SYNCHRONOUS_FUNCTION;

    IntFunction<Boolean> checkFunction = (timeoutForCheck) -> {
      List<CefCookie> cookies = getCookies(url, null, timeoutForCheck);
      if (cookies == null) {
        return false;
      }
      return cookies.contains(cefCookie);
    };

    myLock.lock();
    try {
      Future<@NotNull Boolean> future = myExecutorService.submit(() -> {
        if (checkFunction.apply(timeout / 2)) {
          LOG.debug("Cookie is already set");
          return true;
        }
        if (!myCefCookieManager.setCookie(url, cefCookie)) {
          LOG.error("Posting task to set cookie is failed");
          return false;
        }
        while (myLock.isLocked()) {
          boolean result = checkFunction.apply(timeout / 2);
          if (result) return true;
        }
        return false;
      });

      try {
        return future.get(timeout, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException e) {
        LOG.error("Cookie setting took more than " + timeout + " ms");
        return false;
      }
      catch (InterruptedException e) {
        LOG.error("Cookie setting is interrupted");
        return false;
      }
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
    finally {
      myLock.unlock();
    }
  }

  /**
   * Deletes all cookies for all hosts and domains.
   *
   * @param doSync if false - underlying asynchronous native method {@link CefCookieManager#deleteCookies(String, String)} is used,
   *               true - synchronous {@link JBCefCookieManager#deleteCookies(String, String, IntFunction, Integer)}.
   */
  public boolean deleteCookies(boolean doSync) {
    if (doSync) {
      return deleteCookies("", "", (timeout) -> {
        List<CefCookie> cookies = getCookies(null, false, timeout);
        if (cookies == null) {
          return false;
        }
        return cookies.isEmpty();
      }, null);
    }
    else {
      return myCefCookieManager.deleteCookies("", "");
    }
  }

  /**
   * Deletes all host cookies (but not domain cookies) irrespective of path will be deleted.
   *
   * @param doSync if false - underlying asynchronous native method {@link CefCookieManager#deleteCookies(String, String)} is used,
   *               true - synchronous {@link JBCefCookieManager#deleteCookies(String, String, IntFunction, Integer)}.
   */
  public boolean deleteCookies(@NotNull String url, boolean doSync) {
    if (doSync) {
      return deleteCookies(url, "", (timeout) -> {
        List<CefCookie> cookies = getCookies(url, false, timeout);
        if (cookies == null) {
          return false;
        }
        return cookies.isEmpty();
      }, null);
    }
    else {
      return myCefCookieManager.deleteCookies(url, "");
    }
  }

  /**
   * Deletes all host and domain cookies matching |url| and |cookieName| values.
   *
   * @param doSync if false - underlying asynchronous native method {@link CefCookieManager#deleteCookies(String, String)} is used,
   *               true - synchronous {@link JBCefCookieManager#deleteCookies(String, String, IntFunction, Integer)}.
   */
  public boolean deleteCookies(@NotNull String url, @NotNull String cookieName, boolean doSync) {
    if (doSync) {
      return deleteCookies(url, cookieName, null);
    }
    else {
      return myCefCookieManager.deleteCookies(url, cookieName);
    }
  }

  /**
   * Deletes synchronously all host and domain cookies matching |url| and |cookieName| values with specified timeout.
   *
   * @see JBCefCookieManager#deleteCookies(String, String, IntFunction, Integer)
   */
  public boolean deleteCookies(@NotNull String url, @NotNull String cookieName, @Nullable Integer maxTimeToWait) {
    IntFunction<Boolean> checkFunction = (timeout) -> {
      List<CefCookie> cookies = getCookies(url, false, timeout);
      if (cookies == null) {
        return false;
      }
      return cookies.stream().allMatch(cefCookie -> cefCookie.name != cookieName);
    };

    return deleteCookies(url, cookieName, checkFunction, maxTimeToWait);
  }

  /**
   * Deletes synchronously all host and domain cookies matching |url| and |cookieName| values
   * with specified function for checking and specified timeout.
   * <p>
   * Underlying method {@link CefCookieManager#deleteCookies(String, String)} is asynchronous.
   * This method is synchronous and will wait up to `maxTimeToWait` ms.
   *
   * @param checkFunction function will be used for checking whether cookie deleted
   * @param maxTimeToWait time to wait setting cookie in ms, default value is
   *                      {@link JBCefCookieManager#DEFAULT_TIMEOUT_FOR_SYNCHRONOUS_FUNCTION} ms.
   * @return true if deleting cookies was successful.
   */
  private boolean deleteCookies(@NotNull String url,
                                @NotNull String cookieName,
                                @NotNull IntFunction<Boolean> checkFunction,
                                @Nullable Integer maxTimeToWait) {
    int timeout = maxTimeToWait != null ? maxTimeToWait : DEFAULT_TIMEOUT_FOR_SYNCHRONOUS_FUNCTION;
    myLock.lock();

    try {
      Future<@NotNull Boolean> future = myExecutorService.submit(() -> {
        if (checkFunction.apply(timeout / 2)) {
          LOG.debug("No cookies to be deleted");
          return true;
        }
        if (!myCefCookieManager.deleteCookies(url, cookieName)) {
          LOG.error("Posting task to delete cookies is failed");
          return false;
        }
        while (myLock.isLocked()) {
          boolean result = checkFunction.apply(timeout / 2);
          if (result) return true;
        }
        return false;
      });

      try {
        return future.get(timeout, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException e) {
        LOG.error("Cookie deleting took more than " + timeout + " ms");
        return false;
      }
      catch (InterruptedException e) {
        LOG.error("Cookie deleting is interrupted");
        return false;
      }
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
    finally {
      myLock.unlock();
    }
  }

  private static class JBCookieVisitor implements CefCookieVisitor {
    final List<CefCookie> myCefCookies = new ArrayList<>();
    final CountDownLatch myCountDownLatch;

    JBCookieVisitor(CountDownLatch countDownLatch) {
      myCountDownLatch = countDownLatch;
    }

    @Override
    // TODO[hatari]: This method may never be called if no cookies are found.
    //  So CountDownLatch can't countDown() well except timeout.
    public boolean visit(CefCookie cookie, int count, int total, BoolRef delete) {
      myCefCookies.add(cookie);
      if (count >= total - 1) {
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
