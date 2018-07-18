// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.errorreport.error.UpdateAvailableException;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.security.CompositeX509TrustManager;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.NetUtils;
import com.intellij.util.net.ssl.CertificateUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * @author stathik
 * @since Aug 4, 2003
 */
class ITNProxy {
  private static final String DEFAULT_USER = "idea_anonymous";
  private static final String DEFAULT_PASS = "guest";
  private static final String DEVELOPERS_LIST_URL = "https://ea-engine.labs.intellij.net/data?category=developers";
  private static final String NEW_THREAD_POST_URL = "https://ea-report.jetbrains.com/trackerRpc/idea/createScr";
  private static final String NEW_THREAD_VIEW_URL = "https://ea.jetbrains.com/browser/ea_reports/";

  private static final NotNullLazyValue<Map<String, String>> TEMPLATE = AtomicNotNullLazyValue.createValue(() -> {
    Map<String, String> template = new LinkedHashMap<>();

    template.put("protocol.version", "1");
    template.put("os.name", SystemInfo.OS_NAME);
    template.put("java.version", SystemInfo.JAVA_VERSION);
    template.put("java.vm.vendor", SystemInfo.JAVA_VENDOR);

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    BuildNumber build = appInfo.getBuild();
    String buildNumberWithAllDetails = build.asString();
    if (StringUtil.startsWith(buildNumberWithAllDetails, build.getProductCode() + "-")) {
      buildNumberWithAllDetails = buildNumberWithAllDetails.substring(build.getProductCode().length() + 1);
    }

    template.put("app.name", namesInfo.getProductName());
    template.put("app.name.full", namesInfo.getFullProductName());
    template.put("app.name.version", appInfo.getVersionName());
    template.put("app.eap", Boolean.toString(appInfo.isEAP()));
    template.put("app.internal", Boolean.toString(ApplicationManager.getApplication().isInternal()));
    template.put("app.build", appInfo.getApiVersion());
    template.put("app.version.major", appInfo.getMajorVersion());
    template.put("app.version.minor", appInfo.getMinorVersion());
    template.put("app.build.date", format(appInfo.getBuildDate()));
    template.put("app.build.date.release", format(appInfo.getMajorReleaseBuildDate()));
    template.put("app.compilation.timestamp", IdeaLogger.getOurCompilationTimestamp());
    template.put("app.product.code", build.getProductCode());
    template.put("app.build.number", buildNumberWithAllDetails);

    return template;
  });

  private static @Nullable String format(@Nullable Calendar calendar) {
    return calendar == null ?  null : Long.toString(calendar.getTime().getTime());
  }

  static @NotNull List<Developer> fetchDevelopers(@NotNull ProgressIndicator indicator) throws IOException {
    return HttpRequests.request(DEVELOPERS_LIST_URL).connect(request -> {
      List<Developer> developers = new ArrayList<>();
      developers.add(Developer.NULL);

      String line;
      while ((line = request.getReader().readLine()) != null) {
        int i = line.indexOf('\t');
        if (i == -1) throw new IOException("Protocol error");
        int id = Integer.parseInt(line.substring(0, i));
        String name = line.substring(i + 1);
        developers.add(new Developer(id, name));
        indicator.checkCanceled();
      }

      return developers;
    });
  }

  static class ErrorBean {
    final IdeaLoggingEvent event;
    final String comment;
    final String pluginName;
    final String pluginVersion;
    final String lastActionId;
    final int previousException;

    ErrorBean(IdeaLoggingEvent event, String comment, String pluginName, String pluginVersion, String lastActionId, int previousException) {
      this.event = event;
      this.comment = comment;
      this.pluginName = pluginName;
      this.pluginVersion = pluginVersion;
      this.lastActionId = lastActionId;
      this.previousException = previousException;
    }
  }

  static void sendError(@Nullable Project project,
                        @Nullable String login,
                        @Nullable String password,
                        @NotNull ErrorBean error,
                        @NotNull IntConsumer onSuccess,
                        @NotNull Consumer<Exception> onError) {
    if (StringUtil.isEmptyOrSpaces(login)) {
      login = DEFAULT_USER;
      password = DEFAULT_PASS;
    }
    else if (password == null) {
      password = "";
    }

    String _login = login, _password = password;
    new Task.Backgroundable(project, DiagnosticBundle.message("title.submitting.error.report")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          onSuccess.accept(postNewThread(_login, _password, error));
        }
        catch (Exception ex) {
          onError.accept(ex);
        }
      }
    }.queue();
  }

  static @NotNull String getBrowseUrl(int threadId) {
    return NEW_THREAD_VIEW_URL + threadId;
  }

  private static SSLContext ourSslContext;

  private static int postNewThread(String login, String password, ErrorBean error) throws Exception {
    if (ourSslContext == null) {
      ourSslContext = initContext();
    }

    HttpURLConnection connection = post(new URL(NEW_THREAD_POST_URL), createRequest(login, password, error));
    int responseCode = connection.getResponseCode();
    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw new InternalEAPException(DiagnosticBundle.message("error.http.result.code", responseCode));
    }

    String response = FileUtil.loadTextAndClose(connection.getInputStream());

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

  private static byte[] createRequest(String login, String password, ErrorBean error) throws UnsupportedEncodingException {
    StringBuilder builder = new StringBuilder(8192);

    for (Map.Entry<String, String> entry : TEMPLATE.getValue().entrySet()) {
      append(builder, entry.getKey(), entry.getValue());
    }

    append(builder, "user.login", login);
    append(builder, "user.password", password);

    UpdateSettings updateSettings = UpdateSettings.getInstance();
    append(builder, "update.channel.status", updateSettings.getSelectedChannelStatus().getCode());
    append(builder, "update.ignored.builds", StringUtil.join(updateSettings.getIgnoredBuildNumbers(), ","));

    append(builder, "plugin.name", error.pluginName);
    append(builder, "plugin.version", error.pluginVersion);
    append(builder, "last.action", error.lastActionId);
    if (error.previousException > 0) {
      append(builder, "previous.exception", Integer.toString(error.previousException));
    }

    String message = error.event.getMessage();
    String stacktrace = error.event.getThrowableText();
    boolean redacted = false;
    if (error.event instanceof IdeaReportingEvent) {
      String originalMessage = ((IdeaReportingEvent)error.event).getOriginalMessage();
      String originalStacktrace = ((IdeaReportingEvent)error.event).getOriginalThrowableText();
      boolean messagesDiffer = !Objects.equals(message, originalMessage);
      boolean tracesDiffer = !Objects.equals(stacktrace, originalStacktrace);
      if (messagesDiffer || tracesDiffer) {
        String summary = "";
        if (messagesDiffer) summary += "*** message was redacted (" + diff(originalMessage, message) + ")\n";
        if (tracesDiffer) summary += "*** stacktrace was redacted (" + diff(originalStacktrace, stacktrace) + ")\n";
        message = message != null ? summary + '\n' + message : summary.trim();
        redacted = true;
      }
    }
    append(builder, "error.message", message);
    append(builder, "error.stacktrace", stacktrace);
    append(builder, "error.description", error.comment);
    if (redacted) {
      append(builder, "error.redacted", Boolean.toString(true));
    }

    Object eventData = error.event.getData();
    if (eventData instanceof AbstractMessage) {
      AbstractMessage messageObj = (AbstractMessage)eventData;
      for (Attachment attachment : messageObj.getIncludedAttachments()) {
        append(builder, "attachment.name", attachment.getName());
        append(builder, "attachment.value", attachment.getEncodedBytes());
      }
      if (messageObj.getAssigneeId() != null) {
        append(builder, "assignee.id", Integer.toString(messageObj.getAssigneeId()));
      }
    }

    return builder.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void append(StringBuilder builder, String key, @Nullable String value) throws UnsupportedEncodingException {
    if (StringUtil.isEmpty(value)) return;
    if (builder.length() > 0) builder.append('&');
    builder.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
  }

  private static String diff(String original, String redacted) {
    return "original:" + wc(original) + " submitted:" + wc(redacted);
  }

  private static String wc(String s) {
    return s == null ? "-" : StringUtil.splitByLines(s).length + "/" + s.split("[^\\w']+").length + "/" + s.length();
  }

  private static HttpURLConnection post(URL url, byte[] bytes) throws IOException {
    HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();

    connection.setSSLSocketFactory(ourSslContext.getSocketFactory());
    if (!NetUtils.isSniEnabled()) {
      connection.setHostnameVerifier(new EaHostnameVerifier());
    }

    connection.setRequestMethod("POST");
    connection.setDoInput(true);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=" + StandardCharsets.UTF_8.name());
    connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));

    try (OutputStream out = connection.getOutputStream()) {
      out.write(bytes);
    }

    return connection;
  }

  private synchronized static SSLContext initContext() throws GeneralSecurityException, IOException {
    CertificateFactory cf = CertificateFactory.getInstance(CertificateUtil.X509);
    Certificate ca = cf.generateCertificate(new ByteArrayInputStream(JB_CA_CERT.getBytes(StandardCharsets.US_ASCII)));
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
    @Override
    public boolean verify(String hostname, @NotNull SSLSession session) {
      try {
        Certificate[] certificates = session.getPeerCertificates();
        if (certificates.length > 1) {
          Certificate certificate = certificates[0];
          if (certificate instanceof X509Certificate) {
            String cn = CertificateUtil.getCommonName((X509Certificate)certificate);
            if (cn.endsWith(".jetbrains.com") || cn.endsWith(".intellij.net")) {
              return true;
            }
          }

          Certificate ca = certificates[certificates.length - 1];
          if (ca instanceof X509Certificate) {
            String cn = CertificateUtil.getCommonName((X509Certificate)ca);
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(ca.getEncoded());
            StringBuilder fp = new StringBuilder(2 * digest.length);
            for (byte b : digest) fp.append(Integer.toHexString(b & 0xFF));
            if (JB_CA_CN.equals(cn) && JB_CA_FP.equals(fp.toString())) {
              return true;
            }
          }
        }
      }
      catch (SSLPeerUnverifiedException | CertificateEncodingException | NoSuchAlgorithmException ignored) { }

      return false;
    }
  }

  @SuppressWarnings("SpellCheckingInspection") private static final String JB_CA_CERT =
    "-----BEGIN CERTIFICATE-----\n" +
    "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\n" +
    "-----END CERTIFICATE-----\n";

  private static final String JB_CA_CN = "JetBrains Enterprise CA";
  private static final String JB_CA_FP = "604d3c703a13a3be2d452f14442be11b37e186f";
}