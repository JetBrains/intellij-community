package com.intellij.psi;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
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

  public void testClassShouldNotAppearWithoutEvents_WithPsi() throws IOException {
    final VirtualFile file = myFixture.getTempDirFixture().createFile("Foo.java", "");
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
        PsiManager psiManager = PsiManager.getInstance(getProject());
        PsiModificationTracker tracker = psiManager.getModificationTracker();
        long count1 = tracker.getJavaStructureModificationCount();
        PsiJavaFile psiFile = (PsiJavaFile)psiManager.findFile(file);

        document.insertString(0, "class Foo {}");

        assertEquals(count1, tracker.getJavaStructureModificationCount()); // no PSI changes yet
        //so the class should not exist
        assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
        assertSize(0, psiFile.getClasses());
        assertEquals("", psiManager.findFile(file).getText());
        PlatformTestUtil.tryGcSoftlyReachableObjects();
        
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

        assertFalse(count1 == tracker.getJavaStructureModificationCount());
        assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
        assertEquals("class Foo {}", psiManager.findFile(file).getText());
        assertEquals("class Foo {}", psiManager.findFile(file).getNode().getText());
        assertSize(1, psiFile.getClasses());
      }
    }.execute();
  }
  
  public void testClassShouldNotAppearWithoutEvents_WithoutPsi() throws IOException {
    final GlobalSearchScope allScope = GlobalSearchScope.allScope(getProject());
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    final PsiModificationTracker tracker = psiManager.getModificationTracker();

    final VirtualFile file = myFixture.getTempDirFixture().createFile("Foo.java", "");
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        assertNull(facade.findClass("Foo", allScope));
        long count1 = tracker.getJavaStructureModificationCount();
        
        PlatformTestUtil.tryGcSoftlyReachableObjects();
        assertNull(PsiDocumentManager.getInstance(getProject()).getCachedPsiFile(document));

        document.insertString(0, "class Foo {}");

        assertFalse(count1 == tracker.getJavaStructureModificationCount());
        assertTrue(PsiDocumentManager.getInstance(getProject()).isCommitted(document));
        assertNotNull(facade.findClass("Foo", allScope));

        PsiJavaFile psiFile = (PsiJavaFile)psiManager.findFile(file);
        assertSize(1, psiFile.getClasses());
        assertEquals("class Foo {}", psiFile.getText());
        assertEquals("class Foo {}", psiFile.getNode().getText());
      }
    }.execute();
  }

  public void testClassShouldNotDisappearWithoutEvents() throws IOException {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        PsiModificationTracker tracker = PsiManager.getInstance(getProject()).getModificationTracker();
        long count0 = tracker.getJavaStructureModificationCount();

        final VirtualFile file = myFixture.addFileToProject("Foo.java", "class Foo {}").getVirtualFile();
        final Document document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);

        assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
        long count1 = tracker.getJavaStructureModificationCount();
        assertFalse(count1 == count0);

        document.deleteString(0, document.getTextLength());

        // some plugins (e.g. Copyright) hold file reference in an invokeLater runnable, let them pass 
        UIUtil.dispatchAllInvocationEvents();

        // gc softly-referenced file and AST
        PlatformTestUtil.tryGcSoftlyReachableObjects();
        final PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(getProject());
        assertNull(psiManager.getFileManager().getCachedPsiFile(file));

        assertEquals(count1, tracker.getJavaStructureModificationCount()); // no PSI changes yet
        //so the class should still be there
        assertNotNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
        assertSize(1, ((PsiJavaFile)psiManager.findFile(file)).getClasses());
        assertEquals("class Foo {}", psiManager.findFile(file).getText());
        PlatformTestUtil.tryGcSoftlyReachableObjects();

        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

        assertFalse(count1 == tracker.getJavaStructureModificationCount());
        assertNull(JavaPsiFacade.getInstance(getProject()).findClass("Foo", GlobalSearchScope.allScope(getProject())));
      }
    }.execute();
  }

}
