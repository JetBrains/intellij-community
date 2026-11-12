// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.DataFlavor;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class CopyConcatenatedStringToClipboardIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "concatenation/copy_concatenated_string_to_clipboard");

  @Test
  public void testSimpleLiteral() {
    doTestIntention("simple");
  }

  @Test
  public void testConcatenationWithCaretInLiteral() {
    doTestIntention("not ? yet");
  }

  @Test
  public void testStringTemplate() {
    doTestIntention("Fast, cheap or good? Pick ?.");
  }

  @Test
  public void testStringTemplate2() {
    doTestIntention("""
                       It is difficult
                       to get a person to ? something,
                       when their salary depends upon their not ? it!""");
  }

  private void doTestIntention(String expected) {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Copy string "));
    final Object result = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    assertEquals(expected, result);
  }

  @Test
  public void testSimpleConcatenation() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    IntentionAction action = myFixture.findSingleIntention(
      IntentionPowerPackBundle.message("copy.concatenated.string.to.clipboard.intention.name"));
    myFixture.checkIntentionPreviewHtml(action, "Copy to clipboard the string &quot;&lt;html&gt;<br/>  &lt;body&gt;<br/>?<br/>  &lt;/body&gt;<br/>&lt;/html&gt;<br/>&quot;");
    myFixture.launchAction(action);
    final Object result = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    assertEquals("""
                   <html>
                     <body>
                   ?
                     </body>
                   </html>
                   """, result);
  }
}