package com.intellij.java.injection;

import com.intellij.codeInsight.completion.LightCompletionTestCase;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ref.GCWatcher;

public class JavaInjectedLanguageCompletionTest extends LightCompletionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(getTestRootDisposable());
  }

  public void testDuplicateWordInJavaLiteral() {
    configureFromFileText("x.java", """
      class s {
        String java = "class Foo { Intf i = (a<caret>) -> { return a + ab;}  }";
      }
      """);
    complete();
    assertStringItems("a", "ab");
  }

  public void test_noExceptions_on_reusing_completionCopy_with_invalidated_original_injection() {
    InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(getProject());

    configureFromFileText("a.java", "class C {{ java.util.regex.Pattern.compile(\"\\\\<caret>\"); }}");
    PsiFile injectedFile = getFile();
    Ref<Editor> editorWindowRef = Ref.create(getEditor());

    complete();
    assertContainsItems("A");
    getLookup().hideLookup(true);

    bringRealEditorBack();

    myItems = null;
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    GCWatcher.fromClearedRef(editorWindowRef).ensureCollected();

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      Document document = getEditor().getDocument();
      int offset = document.getText().indexOf("(");
      document.deleteString(offset, offset + 1);

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      assertNull(ilm.findInjectedElementAt(getFile(), getEditor().getCaretModel().getOffset()));

      //todo assertFalse(injectedFile.isValid());

      document.insertString(offset, "(");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      assertNotNull(ilm.findInjectedElementAt(getFile(), getEditor().getCaretModel().getOffset()));
    });

    setupEditorForInjectedLanguage();
    complete();
    assertContainsItems("A"); // and no exceptions!
    assertFalse(injectedFile.isValid());
  }
}
