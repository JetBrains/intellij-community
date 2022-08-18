// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsListener;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.impl.AnnotateIntentionAction;
import com.intellij.codeInsight.intention.impl.DeannotateIntentionAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Trinity;
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
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author anna
 */
public class AddAnnotationFixTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;
  private boolean myExpectedEventWasProduced;
  private boolean myUnexpectedEventWasProduced;
  private MessageBusConnection myBusConnection;

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
      myBusConnection = null;
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
    return Objects.requireNonNull(AddAnnotationPsiFix.getContainer(myFixture.getFile(), myFixture.getCaretOffset()));
  }

  private void startListening(@NotNull final List<Trinity<PsiModifierListOwner, String, Boolean>> expectedSequence) {
    myBusConnection = myFixture.getProject().getMessageBus().connect();
    myBusConnection.subscribe(ExternalAnnotationsManager.TOPIC, new DefaultAnnotationsListener() {
      private int index;

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

  private void startListening(@NotNull PsiModifierListOwner expectedOwner, @NotNull String expectedAnnotationFQName, boolean expectedSuccessful) {
    startListening(Collections.singletonList(Trinity.create(expectedOwner, expectedAnnotationFQName, expectedSuccessful)));
  }

  private void startListeningForExternalChanges() {
    myBusConnection = myFixture.getProject().getMessageBus().connect();
    myBusConnection.subscribe(ExternalAnnotationsManager.TOPIC, new DefaultAnnotationsListener() {
      private boolean notifiedOnce;

      @Override
      public void externalAnnotationsChangedExternally() {
        if (!notifiedOnce) {
          myExpectedEventWasProduced = true;
          notifiedOnce = true;
        }
        else {
          super.externalAnnotationsChangedExternally();
        }
      }
    });
  }

  private void stopListeningAndCheckEvents() {
    myBusConnection.disconnect();
    myBusConnection = null;

    assertTrue(myExpectedEventWasProduced);
    assertFalse(myUnexpectedEventWasProduced);

    myExpectedEventWasProduced = false;
    myUnexpectedEventWasProduced = false;
  }

  public void testAnnotateLibrary() {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    myFixture.configureByFiles("lib/p/Test.java");
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    // expecting other @Nullable annotations to be removed, and default @NotNull to be added
    List<Trinity<PsiModifierListOwner, String, Boolean>> expectedSequence = new ArrayList<>();
    for (String notNull : NullableNotNullManager.getInstance(myFixture.getProject()).getNullables()) {
      expectedSequence.add(Trinity.create(getOwner(), notNull, false));
    }
    expectedSequence.add(Trinity.create(getOwner(), AnnotationUtil.NOT_NULL, true));
    startListening(expectedSequence);
    myFixture.launchAction(getAnnotateAction("NotNull"));

    FileDocumentManager.getInstance().saveAllDocuments();

    final PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    assertNotNull(psiElement);
    final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(psiElement, PsiModifierListOwner.class);
    assertNotNull(listOwner);
    assertNotNull(ExternalAnnotationsManager.getInstance(myFixture.getProject()).findExternalAnnotation(listOwner, AnnotationUtil.NOT_NULL));
    stopListeningAndCheckEvents();

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
    myFixture.launchAction(getAnnotateAction("Deprecated"));
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
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();
    assertNotAvailable("NotNull");
    assertNotAvailable("Nullable");

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertFalse(deannotateFix.isAvailable(myFixture.getProject(), editor, file));
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
    assertTrue(deannotateFix.isAvailable(myFixture.getProject(), editor, file));

    final PsiModifierListOwner container = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    assertNotNull(container);
    startListening(container, AnnotationUtil.NOT_NULL, true);
    ExternalAnnotationsManager.getInstance(myFixture.getProject()).deannotate(container, AnnotationUtil.NOT_NULL);
    stopListeningAndCheckEvents();

    FileDocumentManager.getInstance().saveAllDocuments();

    getAnnotateAction("NotNull");
    getAnnotateAction("Nullable");

    assertFalse(deannotateFix.isAvailable(myFixture.getProject(), editor, file));
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

    startListening(method, AnnotationUtil.NULLABLE, true);
    manager.editExternalAnnotation(method, AnnotationUtil.NULLABLE, annotationFromText.getParameterList().getAttributes());
    stopListeningAndCheckEvents();

    startListening(parameter, AnnotationUtil.NOT_NULL, true);
    manager.editExternalAnnotation(parameter, AnnotationUtil.NOT_NULL, annotationFromText.getParameterList().getAttributes());
    stopListeningAndCheckEvents();

    assertMethodAndParameterAnnotationsValues(manager, method, parameter, "\"bar\"");

    myFixture.checkResultByFile("content/annoMultiRoot/root1/multiRoot/annotations.xml",
                                "content/annoMultiRoot/root1/multiRoot/annotations_after.xml", false);
    myFixture.checkResultByFile("content/annoMultiRoot/root2/multiRoot/annotations.xml",
                                "content/annoMultiRoot/root2/multiRoot/annotations_after.xml", false);
  }

  public void testListenerNotifiedWhenOperationsFail() {
    addLibrary(); // no annotation roots: all operations should fail
    myFixture.configureByFiles("lib/p/Test.java");
    final PsiMethod method = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0];

    startListening(method, AnnotationUtil.NOT_NULL, false);
    ExternalAnnotationsManager.getInstance(myFixture.getProject()).annotateExternally(method, AnnotationUtil.NOT_NULL, myFixture.getFile(), null);
    stopListeningAndCheckEvents();

    startListening(method, AnnotationUtil.NOT_NULL, false);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      ExternalAnnotationsManager.getInstance(myFixture.getProject()).editExternalAnnotation(method, AnnotationUtil.NOT_NULL, null);
    });
    stopListeningAndCheckEvents();

    startListening(method, AnnotationUtil.NOT_NULL, false);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      ExternalAnnotationsManager.getInstance(myFixture.getProject()).deannotate(method, AnnotationUtil.NOT_NULL);
    });
    stopListeningAndCheckEvents();
  }

  public void testListenerNotifiedOnExternalChanges() throws IOException {
    addDefaultLibrary();
    myFixture.configureByFiles("content/anno/p/annotations.xml");
    myFixture.configureByFiles("lib/p/Test.java");

    ExternalAnnotationsManager.getInstance(myFixture.getProject()).findExternalAnnotation(getOwner(), AnnotationUtil.NOT_NULL); // force creating service

    startListeningForExternalChanges();
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
    stopListeningAndCheckEvents();
  } 

  public void testLibraryAnnotationRootsChanged() throws IOException {
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

    startListeningForExternalChanges();
    myFixture.testAction(new CommentByLineCommentAction()); // comment out a line in annotations file
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    annotation = AnnotationUtil.findAnnotation(fooJava, "java.lang.Deprecated");
    assertNull(annotation);
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
    public void externalAnnotationsChangedExternally() {
      System.err.println("Unexpected ExternalAnnotationsListener.externalAnnotationsChangedExternally event produced");
      myUnexpectedEventWasProduced = true;
    }
  }
}