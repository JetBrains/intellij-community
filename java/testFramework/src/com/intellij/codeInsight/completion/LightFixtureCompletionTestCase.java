package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public abstract class LightFixtureCompletionTestCase extends LightCodeInsightFixtureTestCase {
  protected LookupElement[] myItems;

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_6;
  }

  @Override
  protected void tearDown() throws Exception {
    myItems = null;
    super.tearDown();
  }

  protected void configureByFile(String path) {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, com.intellij.openapi.util.text.StringUtil.getShortName(path, '/')));
    complete();
  }

  protected void configureByTestName() {
    configureByFile("/" + getTestName(false) + ".java");
  }

  protected void doAntiTest() {
    configureByTestName();
    checkResultByFile(getTestName(false) + ".java");
    assertEmpty(myItems);
    assertNull(getLookup());
  }

  protected void complete() {
    myItems = myFixture.completeBasic();
  }

  protected void selectItem(LookupElement item) {
    selectItem(item, (char)0);
  }

  protected void checkResultByFile(String path) {
    myFixture.checkResultByFile(path);
  }

  protected void selectItem(LookupElement item, final char completionChar) {
    final LookupImpl lookup = getLookup();
    lookup.setCurrentItem(item);
    if (LookupEvent.isSpecialCompletionChar(completionChar)) {
      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() throws Throwable {
          lookup.finishLookup(completionChar);
        }
      }.execute().throwException();
    } else {
      type(completionChar);
    }
  }

  protected LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup();
  }

  protected void assertFirstStringItems(String... items) {
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertOrderedEquals(strings.subList(0, Math.min(items.length, strings.size())), items);
  }
  protected void assertStringItems(String... items) {
    assertOrderedEquals(myFixture.getLookupElementStrings(), items);
  }

  protected void type(String s) {
    myFixture.type(s);
  }
  protected void type(char c) {
    myFixture.type(c);
  }
}
