/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import junit.framework.TestCase;
import org.jetbrains.annotations.Nls;

import static com.intellij.codeInspection.NlsCapitalizationUtil.isCapitalizationSatisfied;

public class NlsCapitalizationUtilTest extends TestCase {

  public void testEmptyValue() {
    assertSentence("");
    assertTitle("");
  }

  public void testSentence() {
    assertSentence("Word");
    assertSentence("Minimal sentence");
    assertSentence("A bit more normal sentence");
  }

  public void testSentenceNotSatisfied() {
    assertNotCapitalization("all lowercase", Nls.Capitalization.Sentence);
    assertNotCapitalization("All Uppercase", Nls.Capitalization.Sentence);
  }

  public void testSentenceIgnoreUppercaseWords() {
    assertSentence("Fix C issues");
    assertSentence("Fix SQL issues");
    assertSentence("Fix I18n issues");
  }

  public void testSentenceIgnoreNonLetterWords() {
    assertSentence("@charset is invalid");
    assertSentence("Add 'this' qualifier");
  }

  public void testSentenceUppercaseWordsBelowThreshold() {
    assertSentence("One Two three");
    assertSentence("Two Uppercase three lower case");
    assertSentence("Three Uppercase Words two lowercase");
    assertSentence("Please select the configuration file (usually named IntelliLang.xml) to import.");
  }

  public void testSentenceTooManyUppercaseWords() {
    assertNotCapitalization("Four Upper Case Words two lowercase", Nls.Capitalization.Sentence);
  }

  public void testAppleOSDoesntNeedCapitalization() {
    assertTitle("iOS");
    assertTitle("macOS");
  }

  public void testTitle() {
    assertTitle("Word");
    assertTitle("Word Two");
    assertTitle("Word Two   SomeSpaces");
  }

  public void testTitleNotSatisfied() {
    assertNotCapitalization("lowercase", Nls.Capitalization.Title);
    assertNotCapitalization("Word lowercase", Nls.Capitalization.Title);
  }

  public void testFixValueSentence() {
    assertEquals("First word", NlsCapitalizationUtil.fixValue("First Word", Nls.Capitalization.Sentence));
  }

  public void testFixValueTitle() {
    assertEquals("Upper Case", NlsCapitalizationUtil.fixValue("upper case", Nls.Capitalization.Title));
  }

  private static void assertSentence(String value) {
    assertCapitalization(value, Nls.Capitalization.Sentence);
  }

  private static void assertTitle(String value) {
    assertCapitalization(value, Nls.Capitalization.Title);
  }

  private static void assertCapitalization(String value, Nls.Capitalization capitalization) {
    assertTrue("'" + value + "' does not satisfy " + capitalization, isCapitalizationSatisfied(value, capitalization));
  }

  private static void assertNotCapitalization(String value, Nls.Capitalization capitalization) {
    assertFalse("'" + value + "' should not satisfy " + capitalization, isCapitalizationSatisfied(value, capitalization));
  }

  public void testTitleCapitalizationArticles() {
    assertTitle("Compare with the Latest Repository Version");
    assertTitle("A Good Example");
    assertTitle("An Important Message");
  }

  public void testTitleCapitalizationConjunctions() {
    assertTitle("Search and Replace");
    assertTitle("Save or Discard Changes");
    assertTitle("This but Not That");
  }

  public void testTitleCapitalizationPrepositions() {
    assertTitle("Compare with Latest Version");
    assertTitle("Search in Files");
    assertTitle("Go to Declaration");
    assertTitle("Copy from Here");
    assertTitle("Paste as Plain Text");
    assertTitle("Open In…");
  }

  public void testTitleCapitalizationFirstAndLastWords() {
    assertTitle("Compare With");
    assertTitle("The First Word");
    assertTitle("Go to the End");
  }

  public void testTitleCapitalizationNotSatisfied() {
    assertNotCapitalization("Compare With The Latest Repository Version", Nls.Capitalization.Title);
    assertNotCapitalization("Search And Replace", Nls.Capitalization.Title);
    assertNotCapitalization("compare with Latest Version", Nls.Capitalization.Title);
    assertNotCapitalization("Compare With Latest Version", Nls.Capitalization.Title);
    assertNotCapitalization("Compare with", Nls.Capitalization.Title);
    assertNotCapitalization("Save Current Layout As New", Nls.Capitalization.Title);
    assertNotCapitalization("Compare with…", Nls.Capitalization.Title);
    assertNotCapitalization("Open in…", Nls.Capitalization.Title);
  }

  public void testFixValueTitleWithArticles() {
    assertEquals("Compare with the Latest Version", NlsCapitalizationUtil.fixValue("Compare With The Latest Version", Nls.Capitalization.Title));
  }
}