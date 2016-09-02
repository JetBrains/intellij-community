/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.errorreport.crash;

import com.android.tools.analytics.Anonymizer;
import com.android.utils.NullLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * {@link GoogleCrash} provides APIs to upload crash reports to Google crash reporting service.
 * @see <a href="http://go/studio-g3doc/implementation/crash">Crash Backend</a> for more information.
 */
public class GoogleCrash {
  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication() == null;
  private static final boolean DEBUG_BUILD = !UNIT_TEST_MODE && ApplicationManager.getApplication().isInternal();

  // Send crashes during development to the staging backend
  private static final String CRASH_URL =
    (UNIT_TEST_MODE || DEBUG_BUILD) ? "https://clients2.google.com/cr/staging_report" : "https://clients2.google.com/cr/report";

  @Nullable
  private static final String ANONYMIZED_UID = getAnonymizedUid();
  private static final String LOCALE = Locale.getDefault() == null ? "unknown" : Locale.getDefault().toString();

  // The standard keys expected by crash backend. The product id and version are required, others are optional.
  static final String KEY_PRODUCT_ID = "productId";
  static final String KEY_VERSION = "version";
  static final String KEY_EXCEPTION_INFO = "exception_info";

  private static GoogleCrash ourInstance;
  private final String myCrashUrl;

  @Nullable
  private static String getAnonymizedUid() {
    if (UNIT_TEST_MODE) {
      return "UnitTest";
    }

    try {
      return Anonymizer.anonymizeUtf8(new NullLogger(), UpdateChecker.getInstallationUID(PropertiesComponent.getInstance()));
    }
    catch (IOException e) {
      return null;
    }
  }

  private GoogleCrash() {
    this(CRASH_URL);
  }

  @VisibleForTesting
  GoogleCrash(@NotNull String crashUrl) {
    myCrashUrl = crashUrl;
  }

  public CompletableFuture<String> submit(@NotNull CrashReport report) {
    CompletableFuture<String> future = new CompletableFuture<>();
    ForkJoinPool.commonPool().submit(() -> {
      try {
        HttpClient client = HttpClients.createDefault();
        HttpResponse response = client.execute(createPost(report));
        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() >= 300) {
          future.completeExceptionally(new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase()));
          return;
        }

        HttpEntity entity = response.getEntity();
        if (entity == null) {
          future.completeExceptionally(new NullPointerException("Empty response entity"));
          return;
        }

        String reportId = EntityUtils.toString(entity);
        if (DEBUG_BUILD) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Report submitted: http://go/crash-staging/" + reportId);
        }
        future.complete(reportId);
      }
      catch (IOException e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  @NotNull
  private HttpUriRequest createPost(@NotNull CrashReport report) {
    HttpPost post = new HttpPost(myCrashUrl);

    ApplicationInfo applicationInfo = getApplicationInfo();

    String strictVersion = report.version;
    if (strictVersion == null) {
      strictVersion = applicationInfo == null ? "0.0.0.0" : applicationInfo.getStrictVersion();
    }

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();

    // key names recognized by crash
    builder.addTextBody(KEY_PRODUCT_ID, report.productId);
    builder.addTextBody(KEY_VERSION, strictVersion);

    if (ANONYMIZED_UID != null) {
      builder.addTextBody("guid", ANONYMIZED_UID);
    }
    RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    builder.addTextBody("ptime", Long.toString(runtimeMXBean.getUptime()));

    // add report specific data
    report.serialize(builder);

    // product specific key value pairs
    builder.addTextBody("fullVersion", applicationInfo == null ? "0.0.0.0" : applicationInfo.getFullVersion());

    builder.addTextBody("osName", StringUtil.notNullize(SystemInfo.OS_NAME));
    builder.addTextBody("osVersion", StringUtil.notNullize(SystemInfo.OS_VERSION));
    builder.addTextBody("osArch", StringUtil.notNullize(SystemInfo.OS_ARCH));
    builder.addTextBody("locale", StringUtil.notNullize(LOCALE));

    builder.addTextBody("vmName", StringUtil.notNullize(runtimeMXBean.getVmName()));
    builder.addTextBody("vmVendor", StringUtil.notNullize(runtimeMXBean.getVmVendor()));
    builder.addTextBody("vmVersion", StringUtil.notNullize(runtimeMXBean.getVmVersion()));

    MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    builder.addTextBody("heapUsed", Long.toString(usage.getUsed()));
    builder.addTextBody("heapCommitted", Long.toString(usage.getCommitted()));
    builder.addTextBody("heapMax", Long.toString(usage.getMax()));

    post.setEntity(builder.build());
    return post;
  }

  @Nullable
  private static ApplicationInfo getApplicationInfo() {
    // We obtain the ApplicationInfo only if running with an application instance. Otherwise, a call to a ServiceManager never returns..
    return ApplicationManager.getApplication() == null ? null : ApplicationInfo.getInstance();
  }

  public static boolean isReportableCrash(@NotNull Throwable t) {
    return !(t instanceof Logger.EmptyThrowable);
  }

  public static synchronized GoogleCrash getInstance() {
    if (ourInstance == null) {
      ourInstance = new GoogleCrash();
    }

    return ourInstance;
  }
}
