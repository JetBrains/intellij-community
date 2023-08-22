// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;

/**
 * @author Bas Leijdekkers
 */
public class CopyConcatenatedStringToClipboardIntentionTest extends IPPTestCase {

  public void testSimpleLiteral() {
    doTestIntention("simple");
  }

  public void testConcatenationWithCaretInLiteral() {
    doTestIntention("not ? yet");
  }

  public void testStringTemplate() {
    doTestIntention("Fast, cheap or good? Pick ?.");
  }

  public void testStringTemplate2() {
    doTestIntention("""
                       It is difficult
                       to get a person to ? something,
                       when their salary depends upon their not ? it!""");
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  private void doTestIntention(String expected) {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Copy string "));
    final Object result = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    assertEquals(expected, result);
  }

  public void testSimpleConcatenation() {
    myFixture.configureByFile(getTestName(false) + ".java");
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

  @Override
  protected String getRelativePath() {
    return "concatenation/copy_concatenated_string_to_clipboard";
  }
}