package com.intellij.psi;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public class PsiModificationTrackerTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    IdeaTestCase.initPlatformPrefix();
    super.setUp();
  }

  public void testAnnotationNotChanged() throws Exception {
    doReplaceTest("@SuppressWarnings(\"zz\")\n" +
                  "public class Foo { <selection></selection>}",
                  "hi");
  }

  public void testAnnotationNameChanged() throws Exception {
    doReplaceTest("@Suppr<selection>ess</selection>Warnings(\"zz\")\n" +
                  "public class Foo { }",
                  "hi");
  }

  public void testAnnotationParameterChanged() throws Exception {
    doReplaceTest("@SuppressWarnings(\"<selection>zz</selection>\")\n" +
                  "public class Foo { }",
                  "hi");
  }

  public void testAnnotationRemoved() throws Exception {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")</selection>\n" +
                  "public class Foo { }",
                  "");
  }

  public void testAnnotationWithClassRemoved() throws Exception {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")\n" +
                  "public </selection> class Foo { }",
                  "");
  }

  public void testRemoveAnnotatedMethod() throws Exception {
    doReplaceTest("public class Foo {\n" +
                  "  <selection>  " +
                  "   @SuppressWarnings(\"\")\n" +
                  "    public void method() {}\n" +
                  "</selection>" +
                  "}",
                  "");
  }

  public void testRenameAnnotatedMethod() throws Exception {
    doReplaceTest("public class Foo {\n" +
                  "   @SuppressWarnings(\"\")\n" +
                  "    public void me<selection>th</selection>od() {}\n" +
                  "}",
                  "zzz");
  }

  public void testRenameAnnotatedClass() throws Exception {
    doReplaceTest("   @SuppressWarnings(\"\")\n" +
                  "public class F<selection>o</selection>o {\n" +
                  "    public void method() {}\n" +
                  "}",
                  "zzz");
  }

  public void testRemoveAll() throws Exception {
    doReplaceTest("<selection>@SuppressWarnings(\"zz\")\n" +
                  "public  class Foo { }</selection>",
                  "");
  }

  public void testRemoveFile() throws Exception {
    doTest("<selection>@SuppressWarnings(\"zz\")\n" +
           "public  class Foo { }</selection>",
           new Processor<PsiFile>() {
             @Override
             public boolean process(PsiFile psiFile) {
               try {
                 final VirtualFile vFile = psiFile.getVirtualFile();
                 assert vFile != null : psiFile;
                 FileEditorManager.getInstance(getProject()).closeFile(vFile);
                 vFile.delete(this);
               }
               catch (IOException e) {
                 fail(e.getMessage());
               }
               return false;
             }
           });
  }

  private void doReplaceTest(@NonNls String text, @NonNls final String with) {
    doTest(text, new Processor<PsiFile>() {
      @Override
      public boolean process(PsiFile psiFile) {
        replaceSelection(with);
        return false;
      }
    });
  }

  private void doTest(@NonNls String text, Processor<PsiFile> run) {
    PsiFile file = myFixture.configureByText(JavaFileType.INSTANCE, text);
    PsiModificationTracker modificationTracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long count = modificationTracker.getModificationCount();
    run.process(file);
    assertFalse(modificationTracker.getModificationCount() == count);
  }

  private void replaceSelection(final String with) {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        SelectionModel sel = myFixture.getEditor().getSelectionModel();
        myFixture.getEditor().getDocument().replaceString(sel.getSelectionStart(), sel.getSelectionEnd(), with);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    }.execute();
  }

  public void testJavaStructureModificationChangesAfterPackageDelete() {
    PsiFile file = myFixture.addFileToProject("/x/y/Z.java", "text");
    PsiModificationTracker modificationTracker = PsiManager.getInstance(getProject()).getModificationTracker();
    long count = modificationTracker.getJavaStructureModificationCount();

    file.getContainingDirectory().delete();

    assertEquals(count + 1, modificationTracker.getJavaStructureModificationCount());
  }
}
