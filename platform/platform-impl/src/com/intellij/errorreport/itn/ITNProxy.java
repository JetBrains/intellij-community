/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.diagnostic.errordialog.Attachment;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 4, 2003
 * Time: 8:12:00 PM
 * To change this template use Options | File Templates.
 */
public class ITNProxy {
  @NonNls public static final String ENCODE = "UTF8";
  public static final String POST_DELIMETER = "&";

  @NonNls public static final String NEW_THREAD_URL = "http://www.intellij.net/trackerRpc/idea/createScr";

  public static final String THREAD_SUBJECT = "[{0}]";
  @NonNls private static final String HTTP_CONTENT_LENGTH = "Content-Length";
  @NonNls private static final String HTTP_CONTENT_TYPE = "Content-Type";
  @NonNls private static final String HTTP_WWW_FORM = "application/x-www-form-urlencoded";
  @NonNls private static final String HTTP_POST = "POST";

  private static HttpURLConnection post (String url, List<Pair<String,String>> params) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL (url).openConnection();
    connection.setReadTimeout(10 * 1000);
    connection.setConnectTimeout(10 * 1000);
    connection.setRequestMethod(HTTP_POST);
    connection.setDoInput(true);
    connection.setDoOutput(true);
    connection.setRequestProperty(HTTP_CONTENT_TYPE, HTTP_WWW_FORM);

    StringBuilder buffer = new StringBuilder();
    for (Pair<String, String> param : params) {
      if (StringUtil.isNotEmpty(param.first) && StringUtil.isNotEmpty(param.second))
        buffer.append(param.first + "=" + URLEncoder.encode(param.second, ENCODE) + POST_DELIMETER);
      else
        throw new IllegalArgumentException(param.toString());
    }
    connection.setRequestProperty(HTTP_CONTENT_LENGTH, Integer.toString(buffer.length()));
    connection.getOutputStream().write(buffer.toString().getBytes());
    return connection;
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) public static final String SUN = "Sun";
  public static final String JDK_1_2_2 = "1.2.2";
  public static final String JDK_1_3_0 = "1.3.0";
  public static final String JDK_1_3_1 = "1.3.1";
  public static final String JDK_1_3_1_01 = "1.3.1_01";
  public static final String JDK_1_4_0 = "1.4.0";
  public static final String JDK_1_4_0_01 = "1.4.0_01";
  public static final String JDK_1_4_0_02 = "1.4.0_02";
  public static final String JDK_1_4_1 = "1.4.1";
  public static final String JDK_1_4_2 = "1.4.2";

  @NonNls public static final String WINDOWS_XP = "Windows XP";
  @NonNls public static final String WINDOWS_2000 = "Windows 2000";
  @NonNls public static final String WINDOWS_NT = "Windows NT";
  @NonNls public static final String WINDOWS_95 = "Windows 95";
  @NonNls public static final String WINDOWS_98 = "Windows 98";
  @NonNls public static final String WINDOWS_ME = "Windows Me";
  @NonNls public static final String SOLARIS = "Solaris";
  @NonNls public static final String MAC_OS_X = "Mac Os X";
  @NonNls public static final String LINUX = "Linux";

  public static int postNewThread (String userName, String password, ErrorBean error,
                                   String compilationTimestamp)
          throws IOException, NoSuchEAPUserException, InternalEAPException {
    @NonNls List<Pair<String,String>> params = new ArrayList<Pair<String, String>>();
    params.add(Pair.create("username", userName));
    params.add(Pair.create("pwd", password));
    params.add(Pair.create("_title", MessageFormat.format(THREAD_SUBJECT,
                                              error.getLastAction() == null ? error.getExceptionClass() :
                                              error.getLastAction() + ", " + error.getExceptionClass())));
    ApplicationInfoEx appInfo =
      (ApplicationInfoEx) ApplicationManager.getApplication().getComponent(
        ApplicationInfo.class);

    params.add(Pair.create("_build", appInfo.getBuild().asString()));
    params.add(Pair.create("_description",
               (compilationTimestamp != null ? ("Build time: " + compilationTimestamp + "\n") : "") +
               error.getDescription() + "\n\n" + error.getStackTrace()));

    String jdkVersion = SystemProperties.getJavaVersion();
    String jdkVendor = SystemProperties.getJavaVmVendor();

    if (jdkVendor.contains(SUN)) {
      if (jdkVersion.equals(JDK_1_4_2))
        jdkVersion = "10";
      else if (jdkVersion.equals(JDK_1_4_1))
        jdkVersion = "7";
      else if (jdkVersion.equals(JDK_1_4_0_02))
        jdkVersion = "9";
      else if (jdkVersion.equals(JDK_1_4_0_01))
        jdkVersion = "8";
      else if (jdkVersion.equals(JDK_1_4_0))
        jdkVersion = "6";
      else if (jdkVersion.equals(JDK_1_3_1_01))
        jdkVersion = "5";
      else if (jdkVersion.equals(JDK_1_3_1))
        jdkVersion = "4";
      else if (jdkVersion.equals(JDK_1_3_0))
        jdkVersion = "3";
      else if (jdkVersion.equals(JDK_1_2_2))
        jdkVersion = "2";
      else
        jdkVersion = "1";
    } else
      jdkVersion = "1";

    params.add(Pair.create("_jdk", jdkVersion));

    String os = error.getOs();
    if (os == null)
      os = "";

    if (os.contains(WINDOWS_XP))
      os = "4";
    else if (os.contains(WINDOWS_2000) || os.contains(WINDOWS_NT))
      os = "3";
    else if (os.contains(WINDOWS_95) || os.contains(WINDOWS_98) || os.contains(WINDOWS_ME))
      os = "2";
    else if (os.contains(SOLARIS))
      os = "7";
    else if (os.contains(MAC_OS_X))
      os = "6";
    else if (os.contains(LINUX))
      os = "5";
    else
      os = "1";
    params.add(Pair.create("_os", os));

    params.add(Pair.create("_product", ApplicationNamesInfo.getInstance().getProductName()));

    for (Attachment attachment : error.getAttachments()) {
      params.add(Pair.create("_attachment_name", attachment.getName()));
      params.add(Pair.create("_attachment_value", attachment.getEncodedBytes()));
    }
    
    HttpURLConnection connection = post(NEW_THREAD_URL, params);
    int response = connection.getResponseCode();
    switch (response) {
      case HttpURLConnection.HTTP_OK:
        break;
      case HttpURLConnection.HTTP_BAD_REQUEST:
      case HttpURLConnection.HTTP_NOT_FOUND:
        // user not found
        throw new NoSuchEAPUserException(userName);
      default:
        // some problems
        throw new InternalEAPException(DiagnosticBundle.message("error.http.result.code", response));
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    InputStream is = connection.getInputStream();

    int c;
    while ((c = is.read()) != -1) {
      baos.write (c);
    }
    int threadId;

    try {
      threadId = Integer.valueOf(baos.toString().trim()).intValue();
    } catch (NumberFormatException ex) {
      // Tibor!!!! :-E
      throw new InternalEAPException(DiagnosticBundle.message("error.itn.returns.wrong.data"));
    }

    return threadId;
  }
}
