/*
 * User: anna
 * Date: 27-Jun-2007
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.DeannotateIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
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
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AddAnnotationFixTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;
  private Module myModule;
  private Project myProject;
  private boolean myExpectedEventWasProduced = false;
  private boolean myUnexpectedEventWasProduced = false;
  private MessageBusConnection myBusConnection = null;

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
    myProject = myFixture.getProject();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myFixture.tearDown();
    myFixture = null;
    myModule = null;
    myProject = null;
    assertNull(myBusConnection);
  }

  private void addDefaultLibrary() {
    addLibrary("/content/anno");
  }

  private void addLibrary(final @NotNull String... annotationsDirs) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        final LibraryTable libraryTable = model.getModuleLibraryTable();
        final Library library = libraryTable.createLibrary("test");

        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        libraryModel.addRoot(VfsUtil.pathToUrl(myFixture.getTempDirPath() + "/lib"), OrderRootType.SOURCES);
        for (String annotationsDir : annotationsDirs) {
          libraryModel.addRoot(VfsUtil.pathToUrl(myFixture.getTempDirPath() + annotationsDir), AnnotationOrderRootType.getInstance());
        }
        libraryModel.commit();
        model.commit();
      }
    });
  }

  @NotNull
  private PsiModifierListOwner getOwner() {
    CaretModel caretModel = myFixture.getEditor().getCaretModel();
    int position = caretModel.getOffset();
    PsiElement element = myFixture.getFile().findElementAt(position);
    assert element != null;
    PsiModifierListOwner container = AddAnnotationFix.getContainer(element);
    assert container != null;
    return container;
  }

  private void startListening(@NotNull final List<Trinity<PsiModifierListOwner, String, Boolean>> expectedSequence) {
    myBusConnection = myProject.getMessageBus().connect();
    myBusConnection.subscribe(ExternalAnnotationsManager.TOPIC, new DefaultAnnotationsListener() {
      private int index = 0;

      @Override
      public void afterExternalAnnotationChanging(@NotNull PsiModifierListOwner owner, @NotNull String annotationFQName,
                                                  boolean successful) {
        if (index < expectedSequence.size() && expectedSequence.get(index).first == owner
            && expectedSequence.get(index).second.equals(annotationFQName) && expectedSequence.get(index).third == successful) {
          index++;
          myExpectedEventWasProduced = true;
        }
        else {
          super.afterExternalAnnotationChanging(owner, annotationFQName, successful);
        }
      }
    });
  }

  private void startListening(@NotNull final PsiModifierListOwner expectedOwner, @NotNull final String expectedAnnotationFQName,
                              final boolean expectedSuccessful) {
    startListening(Arrays.asList(Trinity.create(expectedOwner, expectedAnnotationFQName, expectedSuccessful)));
  }

  private void stopListeningAndCheckEvents() {
    myBusConnection.disconnect();
    myBusConnection = null;

    assertTrue(myExpectedEventWasProduced);
    assertFalse(myUnexpectedEventWasProduced);

    myExpectedEventWasProduced = false;
    myUnexpectedEventWasProduced = false;
  }

  public void testAnnotateLibrary() throws Throwable {

    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    myFixture.configureByFiles("lib/p/Test.java");
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    final IntentionAction fix = myFixture.findSingleIntention("Annotate method 'get' as @NotNull");
    assertTrue(fix.isAvailable(myProject, editor, file));

    // expecting other @Nullable annotations to be removed, and default @NotNull to be added
    List<Trinity<PsiModifierListOwner, String, Boolean>> expectedSequence
      = new ArrayList<Trinity<PsiModifierListOwner, String, Boolean>>();
    for (String notNull : NullableNotNullManager.getInstance(myProject).getNullables()) {
      expectedSequence.add(Trinity.create(getOwner(), notNull, false));
    }
    expectedSequence.add(Trinity.create(getOwner(), AnnotationUtil.NOT_NULL, true));
    startListening(expectedSequence);
    new WriteCommandAction(myProject){
      @Override
      protected void run(final Result result) throws Throwable {
        fix.invoke(myProject, editor, file);
      }
    }.execute();

    FileDocumentManager.getInstance().saveAllDocuments();

    final PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    assertNotNull(psiElement);
    final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(psiElement, PsiModifierListOwner.class);
    assertNotNull(listOwner);
    assertNotNull(ExternalAnnotationsManager.getInstance(myProject).findExternalAnnotation(listOwner, AnnotationUtil.NOT_NULL));
    stopListeningAndCheckEvents();

    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsAnnotateLibrary_after.xml", false);
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
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();
    assertNotAvailable("Annotate method 'get' as @NotNull");
    assertNotAvailable("Annotate method 'get' as @Nullable");

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertFalse(deannotateFix.isAvailable(myProject, editor, file));
  }

  public void testDeannotation() throws Throwable {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation.java", "Annotate method 'get' as @NotNull", "Annotate method 'get' as @Nullable");
    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsDeannotation_after.xml", false);
  }

  public void testDeannotation1() throws Throwable {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation1.java", "Annotate parameter 'ss' as @NotNull", "Annotate parameter 'ss' as @Nullable");
    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsDeannotation1_after.xml", false);
  }

  private void doDeannotate(@NonNls final String testPath, String hint1, String hint2) throws Throwable {
    myFixture.configureByFile(testPath);
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    assertNotAvailable(hint1);
    assertNotAvailable(hint2);

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertTrue(deannotateFix.isAvailable(myProject, editor, file));

    final PsiModifierListOwner container = DeannotateIntentionAction.getContainer(editor, file);
    startListening(container, AnnotationUtil.NOT_NULL, true);
    new WriteCommandAction(myProject){
      @Override
      protected void run(final Result result) throws Throwable {
        ExternalAnnotationsManager.getInstance(myProject).deannotate(container, AnnotationUtil.NOT_NULL);
      }
    }.execute();
    stopListeningAndCheckEvents();

    FileDocumentManager.getInstance().saveAllDocuments();

    IntentionAction fix = myFixture.findSingleIntention(hint1);
    assertNotNull(fix);

    fix = myFixture.findSingleIntention(hint2);
    assertNotNull(fix);

    assertFalse(deannotateFix.isAvailable(myProject, editor, file));
  }

  public void testReadingOldPersistenceFormat() throws Throwable {
    addDefaultLibrary();
    myFixture.configureByFiles("content/anno/persistence/annotations.xml");
    myFixture.configureByFiles("lib/persistence/Test.java");


    ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(myProject);
    PsiMethod method = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0];
    PsiParameter parameter = method.getParameterList().getParameters()[0];

    assertNotNull(manager.findExternalAnnotation(method, AnnotationUtil.NULLABLE));
    assertNotNull(manager.findExternalAnnotation(method, AnnotationUtil.NLS));
    assertEquals(2, manager.findExternalAnnotations(method).length);

    assertNotNull(manager.findExternalAnnotation(parameter, AnnotationUtil.NOT_NULL));
    assertNotNull(manager.findExternalAnnotation(parameter, AnnotationUtil.NON_NLS));
    assertEquals(2, manager.findExternalAnnotations(parameter).length);
  }

  private static void assertMethodAndParameterAnnotationsValues(ExternalAnnotationsManager manager,
                                                         PsiMethod method,
                                                         PsiParameter parameter,
                                                         String expectedValue) {
    PsiAnnotation methodAnnotation = manager.findExternalAnnotation(method, AnnotationUtil.NULLABLE);
    assertNotNull(methodAnnotation);
    assertEquals(expectedValue, methodAnnotation.findAttributeValue("value").getText());

    PsiAnnotation parameterAnnotation = manager.findExternalAnnotation(parameter, AnnotationUtil.NOT_NULL);
    assertNotNull(parameterAnnotation);
    assertEquals(expectedValue, parameterAnnotation.findAttributeValue("value").getText());
  }

  public void testEditingMultiRootAnnotations() {
    addLibrary("/content/annoMultiRoot/root1", "/content/annoMultiRoot/root2");
    myFixture.configureByFiles("/content/annoMultiRoot/root1/multiRoot/annotations.xml",
                               "/content/annoMultiRoot/root2/multiRoot/annotations.xml");
    myFixture.configureByFiles("lib/multiRoot/Test.java");

    final ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(myProject);
    final PsiMethod method = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0];
    final PsiParameter parameter = method.getParameterList().getParameters()[0];

    assertMethodAndParameterAnnotationsValues(manager, method, parameter, "\"foo\"");

    final PsiAnnotation annotationFromText =
      JavaPsiFacade.getElementFactory(myProject).createAnnotationFromText("@Annotation(value=\"bar\")", null);

    startListening(method, AnnotationUtil.NULLABLE, true);
    new WriteCommandAction(myProject) {
      @Override
      protected void run(final Result result) throws Throwable {
        manager.editExternalAnnotation(method, AnnotationUtil.NULLABLE, annotationFromText.getParameterList().getAttributes());
      }
    }.execute();
    stopListeningAndCheckEvents();

    startListening(parameter, AnnotationUtil.NOT_NULL, true);
    new WriteCommandAction(myProject) {
      @Override
      protected void run(final Result result) throws Throwable {
        manager.editExternalAnnotation(parameter, AnnotationUtil.NOT_NULL, annotationFromText.getParameterList().getAttributes());
      }
    }.execute();
    stopListeningAndCheckEvents();

    assertMethodAndParameterAnnotationsValues(manager, method, parameter, "\"bar\"");

    myFixture.checkResultByFile("content/annoMultiRoot/root1/multiRoot/annotations.xml",
                                "content/annoMultiRoot/root1/multiRoot/annotations_after.xml", false);
    myFixture.checkResultByFile("content/annoMultiRoot/root2/multiRoot/annotations.xml",
                                "content/annoMultiRoot/root2/multiRoot/annotations_after.xml", false);
  }

  private class DefaultAnnotationsListener extends ExternalAnnotationsListener.Adapter {
    @Override
    public void afterExternalAnnotationChanging(@NotNull PsiModifierListOwner owner, @NotNull String annotationFQName,
                                                boolean successful) {
      System.err.println("Unexpected ExternalAnnotationsListener.afterExternalAnnotationChanging event produced");
      System.err.println("owner = [" + owner + "], annotationFQName = [" + annotationFQName + "], successful = [" + successful + "]");
      myUnexpectedEventWasProduced = true;
    }

    @Override
    public void externalAnnotationsChangedDramatically() {
      System.err.println("Unexpected ExternalAnnotationsListener.externalAnnotationsChangedDramatically event produced");
      myUnexpectedEventWasProduced = true;
    }
  }
}
