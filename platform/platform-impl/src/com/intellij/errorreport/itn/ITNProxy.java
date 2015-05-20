/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.errorreport.itn;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.errorreport.error.UpdateAvailableException;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.net.ssl.CertificateUtil;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author stathik
 * @since Aug 4, 2003
 */
public class ITNProxy {
  private static final String NEW_THREAD_VIEW_URL = "https://ea.jetbrains.com/browser/ea_reports/";
  private static final String NEW_THREAD_POST_URL = "https://ea-report.jetbrains.com/trackerRpc/idea/createScr";
  private static final String ENCODING = "UTF8";

  public static void sendError(Project project,
                               final String login,
                               final String password,
                               final ErrorBean error,
                               final Consumer<Integer> callback,
                               final Consumer<Exception> errback) {
    if (StringUtil.isEmpty(login)) {
      return;
    }

    Task.Backgroundable task = new Task.Backgroundable(project, DiagnosticBundle.message("title.submitting.error.report")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          int threadId = postNewThread(login, password, error);
          callback.consume(threadId);
        }
        catch (Exception ex) {
          errback.consume(ex);
        }
      }
    };

    if (project == null) {
      task.run(new EmptyProgressIndicator());
    }
    else {
      ProgressManager.getInstance().run(task);
    }
  }

  public static String getBrowseUrl(int threadId) {
    return NEW_THREAD_VIEW_URL + threadId;
  }

  private static SSLContext ourSslContext;

  private static int postNewThread(String login, String password, ErrorBean error) throws Exception {
    if (ourSslContext == null) {
      ourSslContext = initContext();
    }

    Map<String, String> params = createParameters(login, password, error);
    HttpURLConnection connection = post(new URL(NEW_THREAD_POST_URL), join(params));
    int responseCode = connection.getResponseCode();
    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw new InternalEAPException(DiagnosticBundle.message("error.http.result.code", responseCode));
    }

    String response;
    InputStream is = connection.getInputStream();
    try {
      byte[] bytes = FileUtil.loadBytes(is);
      response = new String(bytes, ENCODING);
    }
    finally {
      is.close();
    }

    if ("unauthorized".equals(response)) {
      throw new NoSuchEAPUserException(login);
    }
    if (response.startsWith("update ")) {
      throw new UpdateAvailableException(response.substring(7));
    }
    if (response.startsWith("message ")) {
      throw new InternalEAPException(response.substring(8));
    }

    try {
      return Integer.valueOf(response.trim()).intValue();
    }
    catch (NumberFormatException ex) {
      throw new InternalEAPException(DiagnosticBundle.message("error.itn.returns.wrong.data"));
    }
  }

  private static Map<String, String> createParameters(String login, String password, ErrorBean error) {
    Map<String, String> params = ContainerUtil.newLinkedHashMap(40);

    params.put("protocol.version", "1");

    params.put("user.login", login);
    params.put("user.password", password);

    params.put("os.name", SystemProperties.getOsName());
    params.put("java.version", SystemProperties.getJavaVersion());
    params.put("java.vm.vendor", SystemProperties.getJavaVmVendor());

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    Application application = ApplicationManager.getApplication();
    params.put("app.name", namesInfo.getProductName());
    params.put("app.name.full", namesInfo.getFullProductName());
    params.put("app.name.version", appInfo.getVersionName());
    params.put("app.eap", Boolean.toString(appInfo.isEAP()));
    params.put("app.internal", Boolean.toString(application.isInternal()));
    params.put("app.build", appInfo.getApiVersion());
    params.put("app.version.major", appInfo.getMajorVersion());
    params.put("app.version.minor", appInfo.getMinorVersion());
    params.put("app.build.date", format(appInfo.getBuildDate()));
    params.put("app.build.date.release", format(appInfo.getMajorReleaseBuildDate()));
    params.put("app.compilation.timestamp", IdeaLogger.getOurCompilationTimestamp());

    BuildNumber build = appInfo.getBuild();
    String buildNumberWithAllDetails = build.asStringWithAllDetails();
    params.put("app.product.code", build.getProductCode());
    if (StringUtil.startsWith(buildNumberWithAllDetails, build.getProductCode() + "-")) {
      buildNumberWithAllDetails = buildNumberWithAllDetails.substring(build.getProductCode().length() + 1);
    }
    params.put("app.build.number", buildNumberWithAllDetails);

    UpdateSettings updateSettings = UpdateSettings.getInstance();
    params.put("update.channel.status", updateSettings.getSelectedChannelStatus().getCode());
    params.put("update.ignored.builds", StringUtil.join(updateSettings.getIgnoredBuildNumbers(), ","));

    params.put("plugin.name", error.getPluginName());
    params.put("plugin.version", error.getPluginVersion());

    params.put("last.action", error.getLastAction());
    params.put("previous.exception", error.getPreviousException() == null ? null : Integer.toString(error.getPreviousException()));

    params.put("error.message", error.getMessage());
    params.put("error.stacktrace", error.getStackTrace());
    params.put("error.description", error.getDescription());

    params.put("assignee.id", error.getAssigneeId() == null ? null : Integer.toString(error.getAssigneeId()));

    for (Attachment attachment : error.getAttachments()) {
      params.put("attachment.name", attachment.getName());
      params.put("attachment.value", attachment.getEncodedBytes());
    }

    return params;
  }

  private static String format(Calendar calendar) {
    return calendar == null ?  null : Long.toString(calendar.getTime().getTime());
  }

  private static byte[] join(Map<String, String> params) throws UnsupportedEncodingException {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> param : params.entrySet()) {
      if (StringUtil.isEmpty(param.getKey())) {
        throw new IllegalArgumentException(param.toString());
      }
      if (builder.length() > 0) {
        builder.append('&');
      }
      if (StringUtil.isNotEmpty(param.getValue())) {
        builder.append(param.getKey()).append('=').append(URLEncoder.encode(param.getValue(), ENCODING));
      }
    }
    return builder.toString().getBytes(ENCODING);
  }

  private static HttpURLConnection post(URL url, byte[] bytes) throws IOException {
    HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();

    connection.setSSLSocketFactory(ourSslContext.getSocketFactory());
    if (!NetUtils.isSniEnabled()) {
      connection.setHostnameVerifier(new EaHostnameVerifier(url.getHost(), "ftp.intellij.net"));
    }

    connection.setRequestMethod("POST");
    connection.setDoInput(true);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=" + ENCODING);
    connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));

    OutputStream out = connection.getOutputStream();
    try {
      out.write(bytes);
    }
    finally {
      out.close();
    }

    return connection;
  }

  private synchronized static SSLContext initContext() throws GeneralSecurityException, IOException {
    CertificateFactory cf = CertificateFactory.getInstance(CertificateUtil.X509);
    Certificate ca = cf.generateCertificate(new ByteArrayInputStream(JB_CA_CERT.getBytes(ENCODING)));
    KeyStore ks = KeyStore.getInstance(CertificateUtil.JKS);
    ks.load(null, null);
    ks.setCertificateEntry("JetBrains CA", ca);
    TrustManagerFactory jbTmf = TrustManagerFactory.getInstance(CertificateUtil.X509);
    jbTmf.init(ks);

    TrustManagerFactory sysTmf = TrustManagerFactory.getInstance(CertificateUtil.X509);
    sysTmf.init((KeyStore)null);

    SSLContext ctx = SSLContext.getInstance("TLS");
    TrustManager composite = new CompositeX509TrustManager(jbTmf.getTrustManagers(), sysTmf.getTrustManagers());
    ctx.init(null, new TrustManager[]{composite}, null);
    return ctx;
  }

  private static class EaHostnameVerifier implements HostnameVerifier {
    private final Set<String> myAllowedHosts;

    public EaHostnameVerifier(@NotNull String... allowedHosts) {
      myAllowedHosts = ContainerUtil.newHashSet(allowedHosts);
    }

    @Override
    public boolean verify(String hostname, SSLSession session) {
      try {
        Certificate[] certificates = session.getPeerCertificates();
        if (certificates.length > 0) {
          Certificate certificate = certificates[0];
          if (certificate instanceof X509Certificate) {
            String cn = CertificateUtil.getCommonName((X509Certificate)certificate);
            return myAllowedHosts.contains(cn);
          }
        }
      }
      catch (SSLPeerUnverifiedException ignored) { }
      return false;
    }
  }

  private static class CompositeX509TrustManager implements X509TrustManager {
    private final List<X509TrustManager> myManagers = ContainerUtil.newArrayList();

    public CompositeX509TrustManager(TrustManager[]... managerSets) {
      for (TrustManager[] set : managerSets) {
        for (TrustManager manager : set) {
          if (manager instanceof X509TrustManager) {
            myManagers.add((X509TrustManager)manager);
          }
        }
      }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      for (X509TrustManager manager : myManagers) {
        try {
          manager.checkServerTrusted(certificates, s);
          return;
        }
        catch (CertificateException ignored) { }
      }
      throw new CertificateException("No trusting managers found for " + s);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      List<X509Certificate> result = ContainerUtil.newArrayList();
      for (X509TrustManager manager : myManagers) {
        ContainerUtil.addAll(result, manager.getAcceptedIssuers());
      }
      return result.toArray(new X509Certificate[result.size()]);
    }
  }

  @SuppressWarnings("SpellCheckingInspection") private static final String JB_CA_CERT =
    "-----BEGIN CERTIFICATE-----\n" +
    "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\n" +
    "-----END CERTIFICATE-----\n";
}
