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
package com.intellij.psi.execution.filters;

import com.intellij.execution.filters.UrlFilter;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

public class UrlFilterTest extends TestCase {

  private static void doUrlTest(@NotNull final String line, @Nullable final String expectedUrl) {
    final Matcher matcher = UrlFilter.URL_PATTERN.matcher(line);
    if (expectedUrl == null) {
      if (matcher.find()) {
        fail("No URL expected in [" + line + "], detected: " + matcher.group());
      }
      return;
    }

    assertTrue("Expected URL (" + expectedUrl + ") is not detected in [" + line + "]", matcher.find());
    assertEquals("Text: [" + line + "]", expectedUrl, matcher.group());
  }

  public void testUrlParsing() throws Exception {
    doUrlTest("not detecting jetbrains.com", null);
    doUrlTest("mailto:admin@jetbrains.com;", "mailto:admin@jetbrains.com");
    doUrlTest("news://jetbrains.com is good", "news://jetbrains.com");
    doUrlTest("see http://www.jetbrains.com", "http://www.jetbrains.com");
    doUrlTest("https://www.jetbrains.com;", "https://www.jetbrains.com");
    doUrlTest("(ftp://jetbrains.com)", "ftp://jetbrains.com");
    doUrlTest("[ftps://jetbrains.com]", "ftps://jetbrains.com");
    doUrlTest("Is it good site:http://jetbrains.com?", "http://jetbrains.com");
    doUrlTest("And http://jetbrains.com?a=@#/%?=~_|!:,.;&b=20,", "http://jetbrains.com?a=@#/%?=~_|!:,.;&b=20");
    doUrlTest("site:www.jetbrains.com.", "www.jetbrains.com");
    doUrlTest("site (www.jetbrains.com)", "www.jetbrains.com");
    doUrlTest("site [www.jetbrains.com]", "www.jetbrains.com");
    doUrlTest("site <www.jetbrains.com>", "www.jetbrains.com");
    doUrlTest("site {www.jetbrains.com}", "www.jetbrains.com");
    doUrlTest("site 'www.jetbrains.com'", "www.jetbrains.com");
    doUrlTest("site \"www.jetbrains.com\"", "www.jetbrains.com");
    doUrlTest("site=www.jetbrains.com!", "www.jetbrains.com");
    doUrlTest("site *www.jetbrains.com*", "www.jetbrains.com");
    doUrlTest("site `www.jetbrains.com`", "www.jetbrains.com");
    doUrlTest("not a site _www.jetbrains.com", null);
    doUrlTest("not a site 1www.jetbrains.com", null);
    doUrlTest("not a site wwww.jetbrains.com", null);
    doUrlTest("not a site xxx.www.jetbrains.com", null);
  }
}
