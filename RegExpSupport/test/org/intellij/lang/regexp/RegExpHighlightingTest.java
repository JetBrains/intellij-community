/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegExpHighlightingTest extends LightPlatformCodeInsightFixtureTestCase {

  private static final ByteArrayOutputStream myOut;

  enum Result {
    OK, ERR
  }

  static class Test {
    final String pattern;
    final Result expectedResult;
    boolean showWarnings = true;
    boolean showInfo = false;
    String regExpHost = null;

    Test(String pattern, Result result, boolean warn, boolean info, String host) {
      this.pattern = pattern;
      expectedResult = result;
      showWarnings = warn;
      showInfo = info;
      regExpHost = host;
    }
  }

  private static final Map<String, Test> myMap = new LinkedHashMap<>();
  static {
    try {
      final Document document = new SAXBuilder().build(new File(PathManager.getHomePath(),"community/RegExpSupport/testData/RETest.xml"));
      final List<Element> list = XPath.selectNodes(document.getRootElement(), "//test");

      int i = 0;
      for (Element element : list) {
        final String name;
        final Element parent = (Element)element.getParent();
        final String s = parent.getName();
        final String t = parent.getAttribute("id") == null ? "" : parent.getAttribute("id").getValue() + "-";
        if (!"tests".equals(s)) {
          name = s + "/test-" + t + ++i + ".regexp";
        }
        else {
          name = "test-" + t + ++i + ".regexp";
        }
        final Result result = Result.valueOf((String)XPath.selectSingleNode(element, "string(expected)"));
        final boolean warn = !"false".equals(element.getAttributeValue("warning"));
        final boolean info = "true".equals(element.getAttributeValue("info"));
        final String host = element.getAttributeValue("host");

        final String pattern = (String)XPath.selectSingleNode(element, "string(pattern)");
        myMap.put(name, new Test(pattern, result, warn, info, host));
        if (!"false".equals(element.getAttributeValue("verify")) && host == null) {
          try {
            Pattern.compile(pattern);
            if (result == Result.ERR) {
              System.out.println("Incorrect FAIL value for " + pattern);
            }
          }
          catch (PatternSyntaxException e) {
            if (result == Result.OK) {
              System.out.println("Incorrect OK value for " + pattern);
            }
          }
        }
      }

      myOut = new ByteArrayOutputStream();
      System.setErr(new PrintStream(myOut));
    }
    catch (JDOMException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testOptions() throws Exception {
    doTest("options/");
  }

  public void testSimple() throws Exception {
    doTest("simple/");
  }

  public void testQuantifiers() throws Exception {
    doTest("quantifiers/");
  }

  public void testGroups() throws Exception {
    doTest("groups/");
  }

  public void testCharclasses() throws Exception {
    doTest("charclasses/");
  }

  public void testEscapes() throws Exception {
    doTest("escapes/");
  }

  public void testNamedchars() throws Exception {
    doTest("namedchars/");
  }

  public void testBackrefs() throws Exception {
    doTest("backrefs/");
  }

  public void testRegressions() throws Exception {
    doTest("regressions/");
  }

  public void testBugs() throws Exception {
    doTest("bug/");
  }

  private void doTest(String prefix) throws IOException {
    int n = 0;
    int failed = 0;
    for (String name : myMap.keySet()) {
      if (prefix == null && name.contains("/")) {
        continue;
      }
      if (prefix != null && !name.startsWith(prefix)) {
        continue;
      }
      System.out.print("filename = " + name);
      n++;

      final Test test = myMap.get(name);
      try {
        if (test.regExpHost != null) {
          final Class<RegExpLanguageHost> aClass = (Class<RegExpLanguageHost>)Class.forName(test.regExpHost);
          final RegExpLanguageHost host = aClass.newInstance();
          RegExpLanguageHosts.setRegExpHost(host);
        }
        try {
          myFixture.configureByText(RegExpFileType.INSTANCE, test.pattern);
          myFixture.testHighlighting(test.showWarnings, true, test.showInfo);
        } finally {
          RegExpLanguageHosts.setRegExpHost(null);
        }

        if (test.expectedResult == Result.ERR) {
          System.out.println("  FAILED. Expression incorrectly parsed OK: " + test.pattern);
          failed++;
        }
        else {
          System.out.println("  OK");
        }
      }
      catch (Throwable e) {
        if (test.expectedResult == Result.ERR) {
          System.out.println("  OK");
        }
        else {
          e.printStackTrace(System.out);
          System.out.println("  FAILED. Expression = " + test.pattern);
          if (myOut.size() > 0) {
            String line;
            final BufferedReader reader = new BufferedReader(new StringReader(myOut.toString()));
            do {
              line = reader.readLine();
            }
            while (line != null && (line.trim().length() == 0 || line.trim().equals("ERROR:")));
            if (line != null) {
              if (line.matches(".*java.lang.Error: junit.framework.AssertionFailedError:.*")) {
                System.out.println("ERROR: " + line.replace("java.lang.Error: junit.framework.AssertionFailedError:", ""));
              }
            }
            else {
              System.out.println("ERROR: " + myOut);
            }
          }
          failed++;
        }
      }
      myOut.reset();
    }

    System.out.println("");
    System.out.println(n + " Tests executed, " + failed + " failed");

    assertFalse(failed > 0);
  }
}
