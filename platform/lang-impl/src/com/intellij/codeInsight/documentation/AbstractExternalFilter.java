/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractExternalFilter {
  private static final Logger LOG = Logger.getInstance(AbstractExternalFilter.class);

  private static final Pattern ourClassDataStartPattern = Pattern.compile("START OF CLASS DATA", Pattern.CASE_INSENSITIVE);
  private static final Pattern ourClassDataEndPattern = Pattern.compile("SUMMARY ========", Pattern.CASE_INSENSITIVE);
  private static final Pattern ourNonClassDataEndPattern = Pattern.compile("<A NAME=", Pattern.CASE_INSENSITIVE);

  @NonNls
  protected static final Pattern ourAnchorSuffix = Pattern.compile("#(.*)$");
  protected static @NonNls final Pattern ourHtmlFileSuffix = Pattern.compile("/([^/]*[.][hH][tT][mM][lL]?)$");
  private static @NonNls final Pattern ourAnnihilator = Pattern.compile("/[^/^.]*/[.][.]/");
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
    Pattern.compile("<meta[^>]+\\s*charset=\"?([\\w\\-]*)\\s*\">", Pattern.CASE_INSENSITIVE);
  private static final String FIELD_SUMMARY = "<!-- =========== FIELD SUMMARY =========== -->";
  private static final String CLASS_SUMMARY = "<div class=\"summary\">";

  protected static abstract class RefConvertor {
    @NotNull
    private final Pattern mySelector;

    public RefConvertor(@NotNull Pattern selector) {
      mySelector = selector;
    }

    protected abstract String convertReference(String root, String href);

    public String refFilter(final String root, String read) {
      String toMatch = StringUtil.toUpperCase(read);
      StringBuilder ready = new StringBuilder();
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
            @Override
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

  protected static String doAnnihilate(String path) {
    int len = path.length();

    do {
      path = ourAnnihilator.matcher(path).replaceAll("/");
    }
    while (len > (len = path.length()));

    return path;
  }

  public String correctRefs(String root, String read) {
    String result = read;

    for (RefConvertor myReferenceConvertor : getRefConverters()) {
      result = myReferenceConvertor.refFilter(root, result);
    }

    return result;
  }

  protected abstract RefConvertor[] getRefConverters();

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getExternalDocInfo(final String url) throws Exception {
    Application app = ApplicationManager.getApplication();
    if (!app.isUnitTestMode() && app.isDispatchThread() || app.isWriteAccessAllowed()) {
      LOG.error("May block indefinitely: shouldn't be called from EDT or under write lock");
      return null;
    }

    if (url == null || !MyJavadocFetcher.ourFree) {
      return null;
    }

    MyJavadocFetcher fetcher = new MyJavadocFetcher(url, new MyDocBuilder() {
      @Override
      public void buildFromStream(String url, Reader input, StringBuilder result) throws IOException {
        doBuildFromStream(url, input, result);
      }
    });
    try {
      app.executeOnPooledThread(fetcher).get();
    }
    catch (Exception e) {
      return null;
    }

    Exception exception = fetcher.myException;
    if (exception != null) {
      fetcher.myException = null;
      throw exception;
    }

    final String docText = correctRefs(ourAnchorSuffix.matcher(url).replaceAll(""), fetcher.data.toString());
    if (LOG.isDebugEnabled()) {
      LOG.debug("Filtered JavaDoc: " + docText + "\n");
    }
    return PlatformDocumentationUtil.fixupText(docText);
  }

  @Nullable
  public String getExternalDocInfoForElement(final String docURL, final PsiElement element) throws Exception {
    return getExternalDocInfo(docURL);
  }

  protected void doBuildFromStream(String url, Reader input, StringBuilder data) throws IOException {
    doBuildFromStream(url, input, data, true, true);
  }

  protected void doBuildFromStream(final String url, Reader input, final StringBuilder data, boolean searchForEncoding, boolean matchStart) throws IOException {
    Trinity<Pattern, Pattern, Boolean> settings = getParseSettings(url);
    @NonNls Pattern startSection = settings.first;
    @NonNls Pattern endSection = settings.second;
    boolean useDt = settings.third;
    @NonNls String greatestEndSection = "<!-- ========= END OF CLASS DATA ========= -->";

    data.append(HTML);
    URL baseUrl = VfsUtilCore.convertToURL(url);
    if (baseUrl != null) {
      data.append("<base href=\"").append(baseUrl).append("\">");
    }
    data.append("<style type=\"text/css\">" +
                "  ul.inheritance {\n" +
                "      margin:0;\n" +
                "      padding:0;\n" +
                "  }\n" +
                "  ul.inheritance li {\n" +
                "       display:inline;\n" +
                "       list-style:none;\n" +
                "  }\n" +
                "  ul.inheritance li ul.inheritance {\n" +
                "    margin-left:15px;\n" +
                "    padding-left:15px;\n" +
                "    padding-top:1px;\n" +
                "  }\n" +
                "</style>");

    String read;
    String contentEncoding = null;
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    BufferedReader buf = new BufferedReader(input);
    do {
      read = buf.readLine();
      if (read != null && searchForEncoding && read.contains("charset")) {
        String foundEncoding = parseContentEncoding(read);
        if (foundEncoding != null) {
          contentEncoding = foundEncoding;
        }
      }
    }
    while (read != null && matchStart && !startSection.matcher(StringUtil.toUpperCase(read)).find());

    if (input instanceof MyReader && contentEncoding != null && !contentEncoding.equalsIgnoreCase(CharsetToolkit.UTF8) &&
        !contentEncoding.equals(((MyReader)input).getEncoding())) {
      //restart page parsing with correct encoding
      try {
        data.setLength(0);
        doBuildFromStream(url, new MyReader(((MyReader)input).myInputStream, contentEncoding), data, false, true);
      }
      catch (ProcessCanceledException e) {
        return;
      }
      return;
    }

    if (read == null) {
      data.setLength(0);
      if (matchStart && input instanceof MyReader) {
        try {
          final MyReader reader = contentEncoding != null ? new MyReader(((MyReader)input).myInputStream, contentEncoding)
                                                          : new MyReader(((MyReader)input).myInputStream, ((MyReader)input).getEncoding());
          doBuildFromStream(url, reader, data, false, false);
        }
        catch (ProcessCanceledException ignored) {}
      }
      return;
    }

    if (useDt) {
      boolean skip = false;

      do {
        if (StringUtil.toUpperCase(read).contains(H2) && !read.toUpperCase(Locale.ENGLISH).contains("H2")) { // read=class name in <H2>
          data.append(H2);
          skip = true;
        }
        else if (endSection.matcher(read).find() || StringUtil.indexOfIgnoreCase(read, greatestEndSection, 0) != -1) {
          data.append(HTML_CLOSE);
          return;
        }
        else if (!skip) {
          appendLine(data, read);
        }
      }
      while (((read = buf.readLine()) != null) && !StringUtil.toUpperCase(read).trim().equals(DL) &&
             !StringUtil.containsIgnoreCase(read, "<div class=\"description\""));

      data.append(DL);

      StringBuilder classDetails = new StringBuilder();
      while (((read = buf.readLine()) != null) && !StringUtil.toUpperCase(read).equals(HR) && !StringUtil.toUpperCase(read).equals(P)) {
        if (reachTheEnd(data, read, classDetails)) return;
        appendLine(classDetails, read);
      }

      while (((read = buf.readLine()) != null) && !StringUtil.toUpperCase(read).equals(P) && !StringUtil.toUpperCase(read).equals(HR)) {
        if (reachTheEnd(data, read, classDetails)) return;
        appendLine(data, read.replaceAll(DT, DT + BR));
      }

      data.append(classDetails);
      data.append(P);
    }
    else {
      appendLine(data, read);
    }

    while (((read = buf.readLine()) != null) &&
           !endSection.matcher(read).find() &&
           StringUtil.indexOfIgnoreCase(read, greatestEndSection, 0) == -1) {
      if (!StringUtil.toUpperCase(read).contains(HR)
          && !StringUtil.containsIgnoreCase(read, "<ul class=\"blockList\">")
          && !StringUtil.containsIgnoreCase(read, "<li class=\"blockList\">")) {
        appendLine(data, read);
      }
    }

    data.append(HTML_CLOSE);
  }

  /**
   * Decides what settings should be used for parsing content represented by the given url.
   *
   * @param url url which points to the target content
   * @return following data: (start interested data boundary pattern; end interested data boundary pattern;
   * replace table data by &lt;dt&gt;)
   */
  @NotNull
  protected Trinity<Pattern, Pattern, Boolean> getParseSettings(@NotNull String url) {
    Pattern startSection = ourClassDataStartPattern;
    Pattern endSection = ourClassDataEndPattern;
    boolean useDt = true;

    Matcher anchorMatcher = ourAnchorSuffix.matcher(url);
    if (anchorMatcher.find()) {
      useDt = false;
      startSection = Pattern.compile(Pattern.quote("<a name=\"" + anchorMatcher.group(1) + "\""), Pattern.CASE_INSENSITIVE);
      endSection = ourNonClassDataEndPattern;
    }
    return Trinity.create(startSection, endSection, useDt);
  }

  private static boolean reachTheEnd(StringBuilder data, String read, StringBuilder classDetails) {
    if (StringUtil.indexOfIgnoreCase(read, FIELD_SUMMARY, 0) != -1 ||
        StringUtil.indexOfIgnoreCase(read, CLASS_SUMMARY, 0) != -1) {
      data.append(classDetails);
      data.append(HTML_CLOSE);
      return true;
    }
    return false;
  }

  @Nullable
  static String parseContentEncoding(@NotNull String htmlLine) {
    if (!htmlLine.contains("charset")) {
      return null;
    }

    Matcher matcher = CHARSET_META_PATTERN.matcher(htmlLine);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static void appendLine(StringBuilder buffer, final String read) {
    buffer.append(read);
    buffer.append("\n");
  }

  private interface MyDocBuilder {
    void buildFromStream(String url, Reader input, StringBuilder result) throws IOException;
  }

  private static class MyJavadocFetcher implements Runnable {
    private static boolean ourFree = true;
    private final StringBuilder data = new StringBuilder();
    private final String url;
    private final MyDocBuilder myBuilder;
    private Exception myException;

    public MyJavadocFetcher(String url, MyDocBuilder builder) {
      this.url = url;
      myBuilder = builder;
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourFree = false;
    }

    @Override
    public void run() {
      try {
        if (url == null) {
          return;
        }

        if (url.startsWith(JAR_PROTOCOL)) {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(BrowserUtil.getDocURL(url));
          if (file != null) {
            myBuilder.buildFromStream(url, new StringReader(VfsUtilCore.loadText(file)), data);
          }
        }
        else {
          URL parsedUrl = BrowserUtil.getURL(url);
          if (parsedUrl != null) {
            HttpRequests.request(parsedUrl.toString()).gzip(false).connect(new HttpRequests.RequestProcessor<Void>() {
              @Override
              public Void process(@NotNull HttpRequests.Request request) throws IOException {
                byte[] bytes = request.readBytes(null);
                String contentEncoding = null;
                ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                try {
                  for (String htmlLine = reader.readLine(); htmlLine != null; htmlLine = reader.readLine()) {
                    contentEncoding = parseContentEncoding(htmlLine);
                    if (contentEncoding != null) {
                      break;
                    }
                  }
                }
                finally {
                  reader.close();
                  stream.reset();
                }

                if (contentEncoding == null) {
                  contentEncoding = request.getConnection().getContentEncoding();
                }

                //noinspection IOResourceOpenedButNotSafelyClosed
                myBuilder.buildFromStream(url, contentEncoding != null ? new MyReader(stream, contentEncoding) : new MyReader(stream), data);
                return null;
              }
            });
          }
        }
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (IOException e) {
        myException = e;
      }
      finally {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourFree = true;
      }
    }
  }

  private static class MyReader extends InputStreamReader {
    private ByteArrayInputStream myInputStream;

    public MyReader(ByteArrayInputStream in) {
      super(in);

      in.reset();
      myInputStream = in;
    }

    public MyReader(ByteArrayInputStream in, String charsetName) throws UnsupportedEncodingException {
      super(in, charsetName);

      in.reset();
      myInputStream = in;
    }
  }
}
