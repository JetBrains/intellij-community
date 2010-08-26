/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Denis Zhdanov
 * @since Aug 25, 2010 3:20:41 PM
 */
public class DefaultLineWrapPositionStrategyTest {

  private static final String MAX_PREFERRED_MARKER = "<PREFERRED>";
  private static final String WRAP_MARKER          = "<WRAP>";

  private DefaultLineWrapPositionStrategy myStrategy;

  @Before
  public void setUp() {
    myStrategy = new DefaultLineWrapPositionStrategy();
  }

  @Test
  public void commaNotSeparated() {
    String document =
      "void method(String <WRAP>p1, String p2) {}";
    doTest(document);
  }

  @Test
  public void wrapOnExceedingWhiteSpace() {
    String document =
      "void method(String p1,<WRAP> String p2) {}";
    doTest(document);
  }

  private void doTest(final String document) {
    doTest(document, true);
  }

  private void doTest(final String document, boolean allowToBeyondMaxPreferredOffset) {
    final Context context = new Context(document);
    context.init();
    myStrategy.calculateWrapPosition(
      context.document, 0, context.document.length(), context.preferredIndex, allowToBeyondMaxPreferredOffset
    );
  }

  /**
   * Utility class for parsing and initialising test data.
   * <p/>
   * <b>Note:</b> this class is line-oriented, i.e. it assumes that target document doesn't contain line feeds.
   */
  private static class Context {

    private final StringBuilder buffer = new StringBuilder();
    private final String rawDocument;

    private String document;
    private int    index;
    private int    wrapIndex;
    private int    preferredIndex;

    Context(String rawDocument) {
      if (rawDocument.contains("\n")) {
        throw new IllegalArgumentException(
          String.format("Don't expect to test multi-line documents but the one is detected: '%s'", rawDocument)
        );
      }
      this.rawDocument = rawDocument;
    }

    public void init() {
      wrapIndex = rawDocument.indexOf(WRAP_MARKER);
      preferredIndex = rawDocument.indexOf(MAX_PREFERRED_MARKER);
      if (wrapIndex >= 0 && preferredIndex >= 0) {
        if (wrapIndex < preferredIndex) {
          processWrap();
          processMaxPreferredIndex();
        }
        else {
          processMaxPreferredIndex();
          processWrap();
        }
      }
      else {
        if (wrapIndex >= 0) {
          processWrap();
        }
        if (preferredIndex >= 0) {
          processMaxPreferredIndex();
        }
      }
      
      buffer.append(rawDocument.substring(index));
      document = buffer.toString();
      if (preferredIndex <= 0) {
        preferredIndex = document.length();
      }
    }

    private void processWrap() {
      buffer.append(rawDocument.substring(index, wrapIndex));
      index = wrapIndex + WRAP_MARKER.length();
      if (rawDocument.indexOf(WRAP_MARKER, index) >= 0) {
        throw new IllegalArgumentException(String.format("More than one wrap indicator is found at the document '%s'", rawDocument));
      }
    }

    private void processMaxPreferredIndex() {
      buffer.append(rawDocument.substring(index, preferredIndex));
      index = preferredIndex + MAX_PREFERRED_MARKER.length();
      if (rawDocument.indexOf(MAX_PREFERRED_MARKER, index) >= 0) {
        throw new IllegalArgumentException(String.format("More than one max preferred offset is found at the document '%s'", rawDocument));
      }
    }
  }
}
