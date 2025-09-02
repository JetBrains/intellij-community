// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ModCommandAwareExternalAnnotationsManager;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.impl.AnnotateIntentionAction;
import com.intellij.codeInsight.intention.impl.DeannotateIntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author anna
 */
public class AddAnnotationFixTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());

    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final String dataPath = PathManagerEx.getTestDataPath() + "/codeInsight/externalAnnotations";
    myFixture.setTestDataPath(dataPath);
    final JavaModuleFixtureBuilder<?> builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);

    myFixture.setUp();

    JavaCodeStyleSettings javaCodeStyleSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    javaCodeStyleSettings.USE_EXTERNAL_ANNOTATIONS = true;
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        javaCodeStyleSettings.USE_EXTERNAL_ANNOTATIONS = false;
      }
    });
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotations);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  private void addDefaultLibrary() {
    addLibrary("/content/anno");
  }

  private void addLibrary(final String @NotNull ... annotationsDirs) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      final LibraryTable libraryTable = model.getModuleLibraryTable();
      final Library library = libraryTable.createLibrary("test");

      final Library.ModifiableModel libraryModel = library.getModifiableModel();
      libraryModel.addRoot(VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/lib"), OrderRootType.SOURCES);
      for (String annotationsDir : annotationsDirs) {
        libraryModel.addRoot(VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + annotationsDir), AnnotationOrderRootType.getInstance());
      }
      libraryModel.commit();
      model.commit();
    });
  }

  @NotNull
  private PsiModifierListOwner getOwner() {
    return requireNonNull(AddAnnotationPsiFix.getContainer(myFixture.getFile(), myFixture.getCaretOffset()));
  }

  public void testAnnotateLibrary() {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    myFixture.configureByFiles("lib/p/Test.java");
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    myFixture.launchAction(getAnnotateAction("NotNull").asIntention());

    // Two ModChooseActions -- first for annotation name, second for annotation root; hence two times async task completion 
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

    final PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    assertNotNull(psiElement);
    final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(psiElement, PsiModifierListOwner.class);
    assertNotNull(listOwner);
    assertNotNull(ExternalAnnotationsManager.getInstance(myFixture.getProject()).findExternalAnnotation(listOwner, AnnotationUtil.NOT_NULL));

    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsAnnotateLibrary_after.xml", false);
  }

  @NotNull
  private AnnotateIntentionAction getAnnotateAction(String annotationShortName) {
    AnnotateIntentionAction action = new AnnotateIntentionAction();
    assertTrue(annotationShortName, action.selectSingle(myFixture.getEditor(), myFixture.getFile(), annotationShortName));
    return action;
  }

  public void testPrimitive() {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestPrimitive.java");
    PsiTestUtil.addSourceRoot(myFixture.getModule(), psiFile.getVirtualFile().getParent());

    assertNotAvailable("NotNull");

    assertFalse(((PsiMethod)getOwner()).isDeprecated());
    myFixture.launchAction(getAnnotateAction("Deprecated").asIntention());
    // Two ModChooseActions -- first for annotation name, second for annotation root; hence two times async task completion 
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    assertTrue(((PsiMethod)getOwner()).isDeprecated());
  }

  public void testAvailableFixesOnClass() {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestPrimitive.java");
    PsiTestUtil.addSourceRoot(myFixture.getModule(), psiFile.getVirtualFile().getParent());
    myFixture.getEditor().getCaretModel().moveToOffset(((PsiJavaFile) psiFile).getClasses()[0].getTextOffset());

    getAnnotateAction("Deprecated");
    assertNotAvailable("NotNull");
    assertNotAvailable("Nullable");
  }

  public void testAvailableFixesOnReference() {
    myFixture.configureByText("Foo.java", "public class Foo {" +
                                          " {\"\".sub<caret>string(1);} " +
                                          "}");
    assertNotAvailable("Deprecated");
    assertNotAvailable("NonNls");
  }

  private void assertNotAvailable(String shortName) {
    AnnotateIntentionAction action = new AnnotateIntentionAction();
    assertThat(action.selectSingle(myFixture.getEditor(), myFixture.getFile(), shortName)).isFalse();
  }

  public void testAnnotated() {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestAnnotated.java");
    PsiTestUtil.addSourceRoot(myFixture.getModule(), psiFile.getVirtualFile().getParent());
    assertNotAvailable("NotNull");
    assertNotAvailable("Nullable");

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertNull(deannotateFix.getPresentation(myFixture.getActionContext()));
  }

  public void testDeannotation() {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation.java");
    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsDeannotation_after.xml", false);
  }

  public void testDeannotation1() {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation1.java");
    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsDeannotation1_after.xml", false);
  }

  private void doDeannotate(@NonNls final String testPath) {
    myFixture.configureByFile(testPath);
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    assertNotAvailable("NotNull");
    assertNotAvailable("Nullable");

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertNotNull(deannotateFix.getPresentation(myFixture.getActionContext()));

    final PsiModifierListOwner container = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    assertNotNull(container);
    ExternalAnnotationsManager.getInstance(myFixture.getProject()).deannotate(container, AnnotationUtil.NOT_NULL);

    FileDocumentManager.getInstance().saveAllDocuments();

    getAnnotateAction("NotNull");
    getAnnotateAction("Nullable");

    assertNull(deannotateFix.getPresentation(myFixture.getActionContext()));
  }

  private static void assertMethodAndParameterAnnotationsValues(ExternalAnnotationsManager manager,
                                                         PsiMethod method,
                                                         PsiParameter parameter,
                                                         String expectedValue) {
    PsiAnnotation methodAnnotation = manager.findExternalAnnotation(method, AnnotationUtil.NULLABLE);
    assertNotNull(methodAnnotation);
    PsiAnnotationMemberValue methodValue = methodAnnotation.findAttributeValue("value");
    assertNotNull(methodValue);
    assertEquals(expectedValue, methodValue.getText());

    PsiAnnotation parameterAnnotation = manager.findExternalAnnotation(parameter, AnnotationUtil.NOT_NULL);
    assertNotNull(parameterAnnotation);
    PsiAnnotationMemberValue parameterValue = parameterAnnotation.findAttributeValue("value");
    assertNotNull(parameterValue);
    assertEquals(expectedValue, parameterValue.getText());
  }

  public void testEditingMultiRootAnnotations() {
    addLibrary("/content/annoMultiRoot/root1", "/content/annoMultiRoot/root2");
    myFixture.configureByFiles("content/annoMultiRoot/root1/multiRoot/annotations.xml",
                               "content/annoMultiRoot/root2/multiRoot/annotations.xml");
    myFixture.configureByFiles("lib/multiRoot/Test.java");

    final ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(myFixture.getProject());
    final PsiMethod method = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0];
    final PsiParameter parameter = method.getParameterList().getParameters()[0];

    assertMethodAndParameterAnnotationsValues(manager, method, parameter, "\"foo\"");

    final PsiAnnotation annotationFromText =
      JavaPsiFacade.getElementFactory(myFixture.getProject()).createAnnotationFromText("@Annotation(value=\"bar\")", null);

    manager.editExternalAnnotation(method, AnnotationUtil.NULLABLE, annotationFromText.getParameterList().getAttributes());

    manager.editExternalAnnotation(parameter, AnnotationUtil.NOT_NULL, annotationFromText.getParameterList().getAttributes());

    assertMethodAndParameterAnnotationsValues(manager, method, parameter, "\"bar\"");

    myFixture.checkResultByFile("content/annoMultiRoot/root1/multiRoot/annotations.xml",
                                "content/annoMultiRoot/root1/multiRoot/annotations_after.xml", false);
    myFixture.checkResultByFile("content/annoMultiRoot/root2/multiRoot/annotations.xml",
                                "content/annoMultiRoot/root2/multiRoot/annotations_after.xml", false);
  }

  public void testNoRootRegisteredPreviously() throws IOException {
    addLibrary(); // no annotation roots: all operations should fail
    myFixture.configureByFiles("lib/p/Test.java");
    final PsiMethod method = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0];

    Project project = myFixture.getProject();
    var manager = ModCommandAwareExternalAnnotationsManager.getInstance(project);
    ModCommand command = manager.annotateExternallyModCommand(method, AnnotationUtil.NOT_NULL, null);
    VirtualFile parentDir = myFixture.getFile().getVirtualFile().getParent();
    VirtualFile annoDir = WriteCommandAction.runWriteCommandAction(
      project,
      (ThrowableComputable<VirtualFile, IOException>)() -> parentDir.createChildDirectory(this, "anno"));
    ModCommand withPath = ((ModEditOptions<?>)command).applyOptions(Map.of("myExternalAnnotationsRoot", annoDir.getPath()));

    assertNull(DumbService.getInstance(project)
                 .computeWithAlternativeResolveEnabled(() -> manager.findExternalAnnotation(method, AnnotationUtil.NOT_NULL)));
    ModCommandExecutor.executeInteractively(ActionContext.from(null, myFixture.getFile()), "", null, () -> withPath);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    assertNotNull(DumbService.getInstance(project)
                 .computeWithAlternativeResolveEnabled(() -> manager.findExternalAnnotation(method, AnnotationUtil.NOT_NULL)));
  }

  public void testListenerNotifiedOnExternalChanges() throws IOException {
    addDefaultLibrary();
    myFixture.configureByFiles("content/anno/p/annotations.xml");
    myFixture.configureByFiles("lib/p/Test.java");

    ExternalAnnotationsManager.getInstance(myFixture.getProject()).findExternalAnnotation(getOwner(), AnnotationUtil.NOT_NULL); // force creating service

    WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myFixture.getTempDirPath() + "/content/anno/p/annotations.xml");
      assert file != null;
      String newText;
      try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
        newText = "  " + StreamUtil.readText(reader) + "      ";
      }
      FileUtil.writeToFile(VfsUtilCore.virtualToIoFile(file), newText);
      file.refresh(false, false);
    });
  } 

  public void testLibraryAnnotationRootsChanged() {
    addDefaultLibrary();
    myFixture.configureByFiles("content/anno/p/annotations.xml");
    PsiFile[] files = myFixture.configureByFiles("lib/p/TestDeannotation.java");

    PsiClass aClass = ((PsiJavaFile)files[0]).getClasses()[0];
    assertNotNull(aClass);
    assertTrue(AnnotationUtil.isAnnotated(aClass.getMethods()[0], AnnotationUtil.NOT_NULL, AnnotationUtil.CHECK_EXTERNAL));

    ModuleRootModificationUtil.updateModel(myFixture.getModule(), model -> {
      final LibraryTable libraryTable = model.getModuleLibraryTable();
      Library library = libraryTable.getModifiableModel().getLibraryByName("test");
      Library.ModifiableModel libraryModifiableModel = library.getModifiableModel();
      libraryModifiableModel.removeRoot(VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/content/anno"),
                                        AnnotationOrderRootType.getInstance());
      libraryModifiableModel.commit();
    });

    assertFalse(AnnotationUtil.isAnnotated(aClass.getMethods()[0], AnnotationUtil.NOT_NULL, AnnotationUtil.CHECK_EXTERNAL));
  }

  public void testAnnotationsUpdatedWhenFileEdited() {
    addDefaultLibrary();
    final PsiFile[] files = myFixture.configureByFiles("content/anno/edit/annotations.xml", "lib/edit/Foo.java");
    final PsiClass fooJava = ((PsiClassOwner)files[1]).getClasses()[0];
    ExternalAnnotationsManager.getInstance(myFixture.getProject());

    PsiAnnotation annotation = AnnotationUtil.findAnnotation(fooJava, "java.lang.Deprecated");
    assertNotNull(annotation);
    assertEquals("java.lang.Deprecated", annotation.getQualifiedName());

    myFixture.testAction(new CommentByLineCommentAction()); // comment out a line in annotations file
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    annotation = AnnotationUtil.findAnnotation(fooJava, "java.lang.Deprecated");
    assertNull(annotation);
  }
}