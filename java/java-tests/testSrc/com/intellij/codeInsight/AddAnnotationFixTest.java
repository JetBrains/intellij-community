/*
 * User: anna
 * Date: 27-Jun-2007
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.DeannotateIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class AddAnnotationFixTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;
  private Module myModule;

  public AddAnnotationFixTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());

    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final String dataPath = PathManagerEx.getTestDataPath() + "/codeInsight/externalAnnotations";
    myFixture.setTestDataPath(dataPath);
    final JavaModuleFixtureBuilder builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);

    myFixture.setUp();
    myModule = builder.getFixture().getModule();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myFixture.tearDown();
    myFixture = null;
    myModule = null;
  }

  protected void addLibrary() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        final LibraryTable libraryTable = model.getModuleLibraryTable();
        final Library library = libraryTable.createLibrary("test");

        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        libraryModel.addRoot(VfsUtil.pathToUrl(myFixture.getTempDirPath() + "/lib"), OrderRootType.SOURCES);
        libraryModel.addRoot(VfsUtil.pathToUrl(myFixture.getTempDirPath() + "/content/anno"), AnnotationOrderRootType.getInstance());
        libraryModel.commit();
        model.commit();
      }
    });
  }

  public void testAnnotateLibrary() throws Throwable {

    addLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    myFixture.configureByFiles("lib/p/Test.java");
    final Project project = myFixture.getProject();
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    final IntentionAction fix = myFixture.findSingleIntention("Annotate method 'get' as @NotNull");
    assertTrue(fix.isAvailable(project, editor, file));

    new WriteCommandAction(project){
      @Override
      protected void run(final Result result) throws Throwable {
        fix.invoke(project, editor, file);
      }
    }.execute();

    FileDocumentManager.getInstance().saveAllDocuments();

    final PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    assertNotNull(psiElement);
    final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(psiElement, PsiModifierListOwner.class);
    assertNotNull(listOwner);
    assertNotNull(ExternalAnnotationsManager.getInstance(project).findExternalAnnotation(listOwner, AnnotationUtil.NOT_NULL));
  }

  public void testPrimitive() throws Throwable {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestPrimitive.java");
    PsiTestUtil.addSourceRoot(myModule, psiFile.getVirtualFile().getParent());

    assertNotAvailable("Annotate method 'get' as @NotNull");
  }

  private void assertNotAvailable(String hint) {
    List<IntentionAction> actions = myFixture.filterAvailableIntentions(hint);
    assertEmpty(actions);
  }

  public void testAnnotated() throws Throwable {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestAnnotated.java");
    PsiTestUtil.addSourceRoot(myModule, psiFile.getVirtualFile().getParent());
    final Project project = myFixture.getProject();
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();
    assertNotAvailable("Annotate method 'get' as @NotNull");
    assertNotAvailable("Annotate method 'get' as @Nullable");

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertFalse(deannotateFix.isAvailable(project, editor, file));
  }

  public void testDeannotation() throws Throwable {
    addLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation.java", "Annotate method 'get' as @NotNull", "Annotate method 'get' as @Nullable");
  }

  public void testDeannotation1() throws Throwable {
    addLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation1.java", "Annotate parameter 'ss' as @NotNull", "Annotate parameter 'ss' as @Nullable");
  }

  private void doDeannotate(@NonNls final String testPath, String hint1, String hint2) throws Throwable {
    myFixture.configureByFile(testPath);
    final Project project = myFixture.getProject();
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    assertNotAvailable(hint1);
    assertNotAvailable(hint2);

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertTrue(deannotateFix.isAvailable(project, editor, file));

    new WriteCommandAction(project){
      @Override
      protected void run(final Result result) throws Throwable {
        ExternalAnnotationsManager.getInstance(project).deannotate(DeannotateIntentionAction.getContainer(editor, file), AnnotationUtil.NOT_NULL);
      }
    }.execute();

    FileDocumentManager.getInstance().saveAllDocuments();

    IntentionAction fix = myFixture.findSingleIntention(hint1);
    assertNotNull(fix);

    fix = myFixture.findSingleIntention(hint2);
    assertNotNull(fix);

    assertFalse(deannotateFix.isAvailable(project, editor, file));
  }
}
