/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.documentation;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.util.io.UrlConnectionUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: May 2, 2003
 * Time: 8:35:34 PM
 * To change this template use Options | File Templates.
 */

public abstract class AbstractExternalFilter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.javadoc.JavaDocExternalFilter");

  protected static @NonNls final Pattern ourAnchorsuffix = Pattern.compile("#(.*)$");
  protected static @NonNls final Pattern ourHTMLFilesuffix = Pattern.compile("/([^/]*[.][hH][tT][mM][lL]?)$");
  private static @NonNls final Pattern ourAnnihilator = Pattern.compile("/[^/^.]*/[.][.]/");
  private static @NonNls final Pattern ourIMGselector = Pattern.compile("<IMG[ \\t\\n\\r\\f]+SRC=\"([^>]*)\"", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
  protected static @NonNls final String DOC_ELEMENT_PROTOCOL = "doc_element://";
  protected static @NonNls final String PSI_ELEMENT_PROTOCOL = "psi_element://";
  private static @NonNls final String JAR_PROTOCOL = "jar:";
  @NonNls private static final String HR = "<HR>";
  @NonNls private static final String P = "<P>";
  @NonNls private static final String DL = "<DL>";
  @NonNls protected static final String H2 = "</H2>";
  @NonNls protected static final String HTML_CLOSE = "</HTML>";
  @NonNls protected static final String HTML = "<HTML>";
  @NonNls private static final String BR = "<BR>";
  @NonNls private static final String DT = "<DT>";
  private static final Pattern CHARSET_META_PATTERN =
    Pattern.compile("<meta.*content\\s*=\".*[;|\\s]*charset=\\s*(.*)\\s*[;|\\s]*\">", Pattern.CASE_INSENSITIVE);

  protected static abstract class RefConvertor {
    private final Pattern mySelector;

    public RefConvertor(Pattern selector) {
      mySelector = selector;
    }

    protected abstract String convertReference(String root, String href);

    public String refFilter(final String root, String read) {
      String toMatch = read.toUpperCase();
      StringBuffer ready = new StringBuffer();
      int prev = 0;
      Matcher matcher = mySelector.matcher(toMatch);

      while (matcher.find()) {
        String before = read.substring(prev, matcher.start(1) - 1);     // Before reference
        final String href = read.substring(matcher.start(1), matcher.end(1)); // The URL
        prev = matcher.end(1) + 1;
        ready.append(before);
        ready.append("\"");
        ready.append(ApplicationManager.getApplication().runReadAction(
            new Computable<String>() {
              public String compute() {
                return convertReference(root, href);
              }
            }
        ));
        ready.append("\"");
      }

      ready.append(read.substring(prev, read.length()));

      return ready.toString();
    }
  }

  protected final RefConvertor myIMGConvertor = new RefConvertor(ourIMGselector) {
    protected String convertReference(String root, String href) {
      if (StringUtil.startsWithChar(href, '#')) {
        return DOC_ELEMENT_PROTOCOL + root + href;
      }

      if (Comparing.strEqual(VirtualFileManager.extractProtocol(root), LocalFileSystem.PROTOCOL)) {
        final String path = VirtualFileManager.extractPath(root);
        if (!path.startsWith("/")) {//skip host for local file system files (format - file://host_name/path)
          root = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, "/" + path);
        }
      }
      return ourHTMLFilesuffix.matcher(root).replaceAll("/") + href;
    }
  };

  protected static String doAnnihilate(String path) {
    int len = path.length();

    do {
      path = ourAnnihilator.matcher(path).replaceAll("/");
    }
    while (len > (len = path.length()));

    return path;
  }

  private String correctRefs(String root, String read) {
    String result = read;

    for (RefConvertor myReferenceConvertor : getRefConvertors()) {
      result = myReferenceConvertor.refFilter(root, result);
    }

    return result;
  }

  protected abstract RefConvertor[] getRefConvertors();

  @Nullable
  private static Reader getReaderByUrl(final String surl, final HttpConfigurable httpConfigurable, final ProgressIndicator pi) throws IOException {
    if (surl.startsWith(JAR_PROTOCOL)) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(BrowserUtil.getDocURL(surl));

      if (file == null) {
        return null;
      }

      return new StringReader(VfsUtil.loadText(file));
    }

    URL url = BrowserUtil.getURL(surl);
    if (url == null) {
      return null;
    }
    httpConfigurable.prepareURL(url.toString());
    final URLConnection urlConnection = url.openConnection();
    final String contentEncoding = urlConnection.getContentEncoding();
    final InputStream inputStream =
      pi != null ? UrlConnectionUtil.getConnectionInputStreamWithException(urlConnection, pi) : urlConnection.getInputStream();
    //noinspection IOResourceOpenedButNotSafelyClosed
    return contentEncoding != null ? new MyReader(inputStream, contentEncoding) : new MyReader(inputStream);
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getExternalDocInfo(final String surl) throws Exception {
    if (surl == null) return null;    
    if (MyJavadocFetcher.isFree()) {
      final MyJavadocFetcher fetcher = new MyJavadocFetcher(surl, new MyDocBuilder() {
        public void buildFromStream(String surl, Reader input, StringBuffer result) throws IOException {
          doBuildFromStream(surl, input, result);
        }
      });
      final Future<?> fetcherFuture = ApplicationManager.getApplication().executeOnPooledThread(fetcher);
      try {
        fetcherFuture.get();
      }
      catch (Exception e) {
        return null;
      }
      final Exception exception = fetcher.getException();
      if (exception != null) {
        fetcher.cleanup();
        throw exception;
      }

      final String docText = correctRefs(ourAnchorsuffix.matcher(surl).replaceAll(""), fetcher.getData());
      if (LOG.isDebugEnabled()) {
        LOG.debug("Filtered JavaDoc: " + docText + "\n");
      }
      return PlatformDocumentationUtil.fixupText(docText);
    }
    return null;
  }

  @Nullable
   public String getExternalDocInfoForElement(final String docURL, final PsiElement element) throws Exception {
     return getExternalDocInfo(docURL);
  }

  protected void doBuildFromStream(String surl, Reader input, StringBuffer data) throws IOException {
    BufferedReader buf = new BufferedReader(input);
    Matcher anchorMatcher = ourAnchorsuffix.matcher(surl);
    @NonNls String startSection = "<!-- ======== START OF CLASS DATA ======== -->";
    @NonNls String endSection = "SUMMARY ========";
    @NonNls String greatestEndSection = "<!-- ========= END OF CLASS DATA ========= -->";
    boolean isClassDoc = true;

    if (anchorMatcher.find()) {
      isClassDoc = false;
      startSection = "<A NAME=\"" + anchorMatcher.group(1).toUpperCase() + "\"";
      endSection = "<A NAME=";
    }

    data.append(HTML);

    String read;
    String charset = null;
    do {
      read = buf.readLine();
      /*if (read != null && read.contains("charset")) {
        charset = read;
      }*/
    }
    while (read != null && read.toUpperCase().indexOf(startSection) == -1);

    if (input instanceof MyReader && charset != null) {
      String contentEncoding = parseContentEncoding(charset);
      if (contentEncoding != null) { //restart page parsing with correct encoding
        input = new MyReader(((MyReader)input).getInputStream(), contentEncoding);
        buf = new BufferedReader(input);
      }
    }

    if (read == null) {
      data.delete(0, data.length());
      return;
    }

    appendLine(data, read);

    if (isClassDoc) {
      boolean skip = false;

      while (((read = buf.readLine()) != null) && !read.toUpperCase().trim().equals(DL)) {
        if (read.toUpperCase().indexOf(H2) != -1) { // read=class name in <H2>
          data.append(H2);
          skip = true;
        }
        else if (!skip) {
          appendLine(data, read);
        }
      }

      data.append(DL);

      StringBuffer classDetails = new StringBuffer();

      while (((read = buf.readLine()) != null) && !read.toUpperCase().equals(HR) && !read.toUpperCase().equals(P)) {
        appendLine(classDetails, read);
      }

      while (((read = buf.readLine()) != null) && !read.toUpperCase().equals(P) && !read.toUpperCase().equals(HR)) {
        appendLine(data, read.replaceAll(DT, DT + BR));
      }

      data.append(classDetails);
      data.append(P);
    }

    while (((read = buf.readLine()) != null) && read.indexOf(endSection) == -1 && read.indexOf(greatestEndSection) == -1) {
      if (read.toUpperCase().indexOf(HR) == -1) {
        appendLine(data, read);
      }
    }

    data.append(HTML_CLOSE);
  }

  @Nullable
  private static String parseContentEncoding(String charset) {
    final Matcher matcher = CHARSET_META_PATTERN.matcher(charset);
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return null;
  }

  private static void appendLine(final StringBuffer buffer, final String read) {
    buffer.append(read);
    buffer.append("\n");
  }

  private interface MyDocBuilder {
    void buildFromStream(String surl, Reader input, StringBuffer result) throws IOException;
  }

  private static class MyJavadocFetcher implements Runnable {
    private static boolean ourFree = true;
    private final StringBuffer data = new StringBuffer();
    private final String surl;
    private final MyDocBuilder myBuilder;
    private final Exception [] myExceptions = new Exception[1];
    private final HttpConfigurable myHttpConfigurable;

    public MyJavadocFetcher(final String surl, MyDocBuilder builder) {
      this.surl = surl;
      myBuilder = builder;
      ourFree = false;
      myHttpConfigurable = HttpConfigurable.getInstance();
    }

    public static boolean isFree() {
      return ourFree;
    }

    public String getData() {
      return data.toString();
    }

    public void run() {
      try {
        if (surl == null) {
          return;
        }

        Reader stream = null;
        try {
          stream = getReaderByUrl(surl, myHttpConfigurable, new ProgressIndicatorBase());
        }
        catch (ProcessCanceledException e) {
          return;
        }
        catch (IOException e) {
          myExceptions[0] = e;
        }

        if (stream == null) {
          return;
        }

        try {
          myBuilder.buildFromStream(surl, stream, data);
        }
        catch (final IOException e) {
          myExceptions[0] = e;
        }
        finally {
          try {
            stream.close();
          }
          catch (IOException e) {
            myExceptions[0] = e;
          }
        }
      }
      finally {
        ourFree = true;
      }
    }

    public Exception getException() {
      return myExceptions[0];
    }

    public void cleanup() {
      myExceptions[0] = null;
    }
  }

  private static class MyReader extends InputStreamReader {
    private InputStream myInputStream;

    public MyReader(InputStream in) {
      super(in);
      myInputStream = in;
    }

    public MyReader(InputStream in, String charsetName) throws UnsupportedEncodingException {
      super(in, charsetName);
      myInputStream = in;
    }

    public InputStream getInputStream() {
      return myInputStream;
    }
  }
}
