package com.intellij.json;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonEditingTest extends JsonTestCase {

  private void doTest(@NotNull final String characters) {
    final String testName = "editing/" + getTestName(false);
    myFixture.configureByFile(testName + ".json");
    final int offset = myFixture.getEditor().getCaretModel().getOffset();
    WriteCommandAction.runWriteCommandAction(null, new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.type(characters);
        return myFixture.getFile();
      }
    });
    myFixture.checkResultByFile(testName + ".after.json");
  }

  public void testContinuationIndentAfterPropertyKey() {
    doTest("\n");
  }

  public void testContinuationIndentAfterColon() {
    doTest("\n");
  }

  // IDEA-130594
  public void testNormalIndentAfterPropertyWithoutComma() {
    doTest("\n");
  }
}
