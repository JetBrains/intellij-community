package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;

public abstract class NormalCompletionTestCase extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/";
  }

  public void configure() {
    configureByTestName();
  }

  public void checkResult() {
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void doTest(String finishChar) {
    configure();
    type(finishChar);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResult();
  }

  public void doTest() {
    configure();
    checkResult();
  }

  public static LookupElementPresentation renderElement(final LookupElement e) {
    return ReadAction.compute(() -> FileBasedIndex.getInstance()
      .ignoreDumbMode(DumbModeAccessType.RELIABLE_DATA_ONLY, () -> LookupElementPresentation.renderElement(e)));
  }
}
