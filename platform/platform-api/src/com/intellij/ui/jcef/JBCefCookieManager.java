// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.cef.callback.CefCookieVisitor;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static com.intellij.openapi.util.Clock.getTime;
import static com.intellij.util.ObjectUtils.notNull;

/**
 * A wrapper over {@link CefCookieManager}.
 * <p>
 * Use {@link #getCookies(String, Boolean)} for retrieving cookies.
 * <p>
 * Use {@link #setCookie(String, JBCefCookie)} for setting cookie.
 * <p>
 * Use {@link #deleteCookies(String, String)} for deleting cookies.
 *
 * @author Aleksey.Rostovskiy
 * @author tav
 */
public final class JBCefCookieManager {
  private static final int DEFAULT_TIMEOUT_MS = 200;
  private static final int BOUNCE_TIMEOUT_MS = 50;

  private static final Logger LOG = Logger.getInstance(JBCefCookieManager.class);

  private final ReentrantLock myLock = new ReentrantLock();
  private final ExecutorService myExecutorService = AppExecutorUtil.getAppScheduledExecutorService();

  private final @NotNull CefCookieManager myCefCookieManager;

  static {
    // assure initialized
    JBCefApp.getInstance();
  }

  public JBCefCookieManager() {
    this(CefCookieManager.getGlobalManager());
  }

  JBCefCookieManager(@NotNull CefCookieManager cefCookieManager) {
    myCefCookieManager = cefCookieManager;
  }

  @SuppressWarnings("unused")
  @NotNull
  public CefCookieManager getCefCookieManager() {
    return myCefCookieManager;
  }

  /**
   * Retrieves cookies asynchronously.
   *
   * @param url             filter by the given url scheme, host, domain and path.
   * @param includeHttpOnly include only true HTTP-only cookies.
   * @return a future with the list of {@link JBCefCookie} which can be empty if cookies cannot be accessed or do not exist
   */
  public @NotNull Future<@NotNull List<JBCefCookie>> getCookies(@Nullable String url, @Nullable Boolean includeHttpOnly) {
    JBCookieVisitor cookieVisitor = new JBCookieVisitor();
    boolean result;
    if (url != null) {
      result = myCefCookieManager.visitUrlCookies(url, notNull(includeHttpOnly, Boolean.FALSE), cookieVisitor);
    }
    else {
      result = myCefCookieManager.visitAllCookies(cookieVisitor);
    }
    if (!result) {
      LOG.debug("Cookies cannot be accessed");
      return CompletableFuture.completedFuture(Collections.emptyList());
    }
    Ref<Future<@NotNull List<JBCefCookie>>> futureRef = new Ref<>();
    futureRef.set(myExecutorService.submit(() -> cookieVisitor.get(() -> futureRef.get() == null || !futureRef.get().isDone())));
    return futureRef.get();
  }

  /**
   * @see JBCefCookieManager#getCookies(String, Boolean, Integer)
   * @deprecated use {@link #getCookies(String, Boolean)}
   */
  @Deprecated
  public @NotNull List<JBCefCookie> getCookies() {
    return getCookies(null, false, null);
  }

  /**
   * @see JBCefCookieManager#getCookies(String, Boolean, Integer)
   * @deprecated use {@link #getCookies(String, Boolean)}
   */
  @Deprecated
  public @NotNull List<JBCefCookie> getCookies(@NotNull String url) {
    return getCookies(url, false, null);
  }

  /**
   * WARNING: the method can lead to a freeze when called from a browser callback.
   *
   * Gets cookies. Underlying native method is asynchronous.
   * This method is executed with synchronization and can take up to `maxTimeToWait` ms.
   *
   * @param url             filter by the given url scheme, host, domain and path.
   * @param includeHttpOnly include only true HTTP-only cookies.
   * @param maxTimeToWait   time to wait getting cookies in ms, or default
   * @return list of {@link JBCefCookie} or null if cookies cannot be accessed
   *
   * @deprecated use {@link #getCookies(String, Boolean)}
   */
  @Deprecated
  public @NotNull List<JBCefCookie> getCookies(@Nullable String url, @Nullable Boolean includeHttpOnly, @Nullable Integer maxTimeToWait) {
    boolean httpOnly = notNull(includeHttpOnly, Boolean.FALSE);
    JBCookieVisitor cookieVisitor = new JBCookieVisitor();
    boolean result;
    if (url != null) {
      result = myCefCookieManager.visitUrlCookies(url, httpOnly, cookieVisitor);
    }
    else {
      result = myCefCookieManager.visitAllCookies(cookieVisitor);
    }
    if (!result) {
      LOG.debug("Cookies cannot be accessed");
      return Collections.emptyList();
    }
    return cookieVisitor.get(notNull(maxTimeToWait, DEFAULT_TIMEOUT_MS));
  }

  /**
   * Sets a cookie asynchronously given a valid URL and explicit user-provided cookie attributes.
   * The method expects each attribute to be well-formed. It will check for disallowed characters
   * (e.g. the ';' character is disallowed within the cookie value attribute) and fail without setting
   * the cookie if such characters are found.
   *
   * It's recommended that a caller of the method either waits for the returned {@code future} to complete
   * or cancels it when no confirmation of the success is required. Otherwise, it is possible that
   * the confirmation task performs infinitely in case something went wrong with the setting.
   *
   * @param url the cookie URL (should match the cookie's domain)
   * @param jbCefCookie the cookie
   * @return a future with false if an invalid URL is specified or if cookies cannot be accessed.
   */
  public @NotNull Future<@NotNull Boolean> setCookie(@NotNull String url, @NotNull JBCefCookie jbCefCookie) {
    if (!checkArgs(url, jbCefCookie)) {
      return CompletableFuture.completedFuture(false);
    }
    if (!myCefCookieManager.setCookie(url, jbCefCookie.getCefCookie())) {
      LOG.error("Posting task to set cookie is failed");
      return CompletableFuture.completedFuture(false);
    }
    Ref<Future<@NotNull Boolean>> futureRef = new Ref<>();
    futureRef.set(myExecutorService.submit(() -> {
      while (futureRef.get() == null || !futureRef.get().isDone()) {
        if (getCookies(url, null, BOUNCE_TIMEOUT_MS).contains(jbCefCookie)) {
          return true;
        }
      }
      return false;
    }));
    return futureRef.get();
  }

  /**
   * @see JBCefCookieManager#setCookie(String, JBCefCookie, Integer)
   *
   * @deprecated use {@link #setCookie(String, JBCefCookie)}
   */
  @Deprecated
  public boolean setCookie(@NotNull String url, @NotNull JBCefCookie jbCefCookie, boolean doSync) {
    if (doSync) {
      return setCookie(url, jbCefCookie, null);
    }
    else {
      return myCefCookieManager.setCookie(url, jbCefCookie.getCefCookie());
    }
  }

  /**
   * WARNING: the method can lead to a freeze when called from a browser callback.
   *
   * Sets a cookie given a valid URL and explicit user-provided cookie attributes.
   * Underlying native method {@link CefCookieManager#setCookie(String, CefCookie)} is asynchronous.
   * This method is synchronous and will wait up to `maxTimeToWait` ms.
   *
   * @param maxTimeToWait time to wait setting cookie in ms, or default
   * @return true if setting the cookie was successful.
   *
   * @deprecated use {@link #setCookie(String, JBCefCookie)}
   */
  @Deprecated
  public boolean setCookie(@NotNull String url, @NotNull JBCefCookie jbCefCookie, @Nullable Integer maxTimeToWait) {
    if (!checkArgs(url, jbCefCookie)) return false;

    int timeout = notNull(maxTimeToWait, DEFAULT_TIMEOUT_MS);

    IntFunction<Boolean> checkFunction = (timeoutForCheck) -> getCookies(url, null, timeoutForCheck).contains(jbCefCookie);

    myLock.lock();
    try {
      Future<@NotNull Boolean> future = myExecutorService.submit(() -> {
        if (checkFunction.apply(timeout / 2)) {
          LOG.debug("Cookie is already set");
          return true;
        }
        if (!myCefCookieManager.setCookie(url, jbCefCookie.getCefCookie())) {
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

  private static boolean checkArgs(@NotNull String url, @NotNull JBCefCookie jbCefCookie) {
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      String domain = uri.getHost();
      domain = domain.startsWith("www") ? domain : "." + domain;

      if (scheme.equals("https") && !jbCefCookie.isSecure()) {
        LOG.warn("Cannot set cookie without secure flag for HTTPS web-site");
        return false;
      }
      if (!domain.contains(jbCefCookie.getDomain())) {
        LOG.warn("Cookie domain `" + jbCefCookie.getDomain() + "` doesn't match URL host `" + domain + "`");
        return false;
      }
    }
    catch (URISyntaxException e) {
      LOG.error(e);
      return false;
    }
    return true;
  }

  /**
   * Deletes asynchronously all cookies that match the specified parameters. If both {@code url] and {@code cookieName} values
   * are specified all host and domain cookies matching both will be deleted. If only {@code url} is
   * specified all host cookies (but not domain cookies) irrespective of path will be deleted. If
   * {@code url} is empty all cookies for all hosts and domains will be deleted. Cookies can alternately
   * be deleted using the visit*Cookies() methods.
   *
   * It's recommended that a caller of the method either waits for the returned {@code future} to complete
   * or cancels it when no confirmation of the success is required. Otherwise, it is possible that
   * the confirmation task performs infinitely in case something went wrong with the deletion.
   *
   * @param url The cookie URL to delete or null.
   * @param cookieName The cookie name to delete or null.
   * @return a future with false if a non-empty invalid URL is specified or if cookies cannot be accessed.
   */
  public @NotNull Future<@NotNull Boolean> deleteCookies(@Nullable String url, @Nullable String cookieName) {
    if (!myCefCookieManager.deleteCookies(url, cookieName)) {
      LOG.error("Posting task to delete cookies is failed");
      return CompletableFuture.completedFuture(false);
    }
    Ref<Future<@NotNull Boolean>> futureRef = new Ref<>();
    futureRef.set(myExecutorService.submit(() -> {
      while (futureRef.get() == null || !futureRef.get().isDone()) {
        if (!ContainerUtil.exists(getCookies(url, false, BOUNCE_TIMEOUT_MS),
                                  cookie -> cookie.getName().equals(cookieName)))
        {
          return true;
        }
      }
      return false;
    }));
    return futureRef.get();
  }

  /**
   * Deletes all cookies for all hosts and domains.
   *
   * @param doSync if false - underlying asynchronous native method {@link CefCookieManager#deleteCookies(String, String)} is used,
   *               true - synchronous {@link JBCefCookieManager#deleteCookies(String, String, IntFunction, Integer)}.
   * @deprecated use {@link #deleteCookies(String, String)}
   */
  @Deprecated
  public boolean deleteCookies(boolean doSync) {
    if (doSync) {
      return deleteCookies(null, null, (timeout) -> getCookies(null, false, timeout).isEmpty(), null);
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
   *
   * @deprecated use {@link #deleteCookies(String, String)}
   */
  @Deprecated
  public boolean deleteCookies(@Nullable String url, boolean doSync) {
    if (doSync) {
      return deleteCookies(url, "", (timeout) -> getCookies(url, false, timeout).isEmpty(), null);
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
   *
   * @deprecated use {@link #deleteCookies(String, String)}
   */
  @Deprecated
  public boolean deleteCookies(@Nullable String url,
                               @Nullable String cookieName,
                               boolean doSync)
  {
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
   *
   * @deprecated use {@link #deleteCookies(String, String)}
   */
  @Deprecated
  public boolean deleteCookies(@Nullable String url,
                               @Nullable String cookieName,
                               @Nullable Integer maxTimeToWait)
  {
    IntFunction<Boolean> checkFunction = (timeout) -> {
      List<JBCefCookie> cookies = getCookies(url, false, timeout);
      return !ContainerUtil.exists(cookies, cefCookie -> cefCookie.getName().equals(cookieName));
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
   *                      {@link JBCefCookieManager#DEFAULT_TIMEOUT_MS} ms.
   * @return true if deleting cookies was successful.
   */
  private boolean deleteCookies(@Nullable String url,
                                @Nullable String cookieName,
                                @NotNull IntFunction<Boolean> checkFunction,
                                @Nullable Integer maxTimeToWait) {
    int timeout = notNull(maxTimeToWait, DEFAULT_TIMEOUT_MS);
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
    final @NotNull List<JBCefCookie> myCefCookies = Collections.synchronizedList(new ArrayList<>());
    final @NotNull CountDownLatch myCountDownLatch;
    long myStartTime = getTime();

    JBCookieVisitor() {
      myCountDownLatch = new CountDownLatch(1);
    }

    @Override
    // TODO[hatari]: This method may never be called if no cookies are found.
    //  So CountDownLatch can't countDown() well except timeout.
    public boolean visit(CefCookie cookie, int count, int total, BoolRef delete) {
      myCefCookies.add(new JBCefCookie(cookie));
      if (myCefCookies.size() >= total) {
        myCountDownLatch.countDown();
      }
      return true;
    }

    public @NotNull List<JBCefCookie> get(@NotNull Supplier<Boolean> condition) {
      while (condition.get()) {
        try {
          if (myCountDownLatch.await(BOUNCE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return myCefCookies;
          }
        }
        catch (InterruptedException e) {
          LOG.error("Cookie visiting is interrupted");
          break;
        }
      }
      return Collections.emptyList();
    }

    public @NotNull List<JBCefCookie> get(int timeout) {
      boolean result;
      try {
        result = myCountDownLatch.await(timeout, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException e) {
        LOG.error("Cookie visiting is interrupted");
        return Collections.emptyList();
      }

      long timeSpent = getTime() - myStartTime;
      if (!result) {
        LOG.debug("Timeout for cookie visiting is reached, " + timeSpent + " ms time spent");
      }
      else {
        LOG.debug("Cookie getting took " + timeSpent + " ms");
      }
      return myCefCookies;
    }
  }
}
