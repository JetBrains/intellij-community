/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsListener;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
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
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author anna
 * @since 27-Jun-2007
 */
public class AddAnnotationFixTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;
  private Module myModule;
  private Project myProject;
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
    final JavaModuleFixtureBuilder builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);

    myFixture.setUp();
    myModule = builder.getFixture().getModule();
    myProject = myFixture.getProject();

    CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS = true;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS = false;
    try {
      myFixture.tearDown();
    }
    finally {
      myFixture = null;
      myModule = null;
      myProject = null;
      myBusConnection = null;

      super.tearDown();
    }
  }

  private void addDefaultLibrary() {
    addLibrary("/content/anno");
  }

  private void addLibrary(@NotNull final String... annotationsDirs) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
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
    return ObjectUtils.assertNotNull(AddAnnotationPsiFix.getContainer(myFixture.getFile(), myFixture.getCaretOffset()));
  }

  private void startListening(@NotNull final List<Trinity<PsiModifierListOwner, String, Boolean>> expectedSequence) {
    myBusConnection = myProject.getMessageBus().connect();
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
    startListening(Arrays.asList(Trinity.create(expectedOwner, expectedAnnotationFQName, expectedSuccessful)));
  }

  private void startListeningForExternalChanges() {
    myBusConnection = myProject.getMessageBus().connect();
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
    for (String notNull : NullableNotNullManager.getInstance(myProject).getNullables()) {
      expectedSequence.add(Trinity.create(getOwner(), notNull, false));
    }
    expectedSequence.add(Trinity.create(getOwner(), AnnotationUtil.NOT_NULL, true));
    startListening(expectedSequence);
    myFixture.launchAction(myFixture.findSingleIntention("Annotate method 'get' as @NotNull"));

    FileDocumentManager.getInstance().saveAllDocuments();

    final PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    assertNotNull(psiElement);
    final PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(psiElement, PsiModifierListOwner.class);
    assertNotNull(listOwner);
    assertNotNull(ExternalAnnotationsManager.getInstance(myProject).findExternalAnnotation(listOwner, AnnotationUtil.NOT_NULL));
    stopListeningAndCheckEvents();

    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsAnnotateLibrary_after.xml", false);
  }

  public void testPrimitive() {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestPrimitive.java");
    PsiTestUtil.addSourceRoot(myModule, psiFile.getVirtualFile().getParent());

    assertNotAvailable("Annotate method 'get' as @NotNull");

    assertFalse(((PsiMethod)getOwner()).isDeprecated());
    myFixture.launchAction(myFixture.findSingleIntention("Annotate method 'get' as @Deprecated"));
    assertTrue(((PsiMethod)getOwner()).isDeprecated());
  }

  public void testAvailableFixesOnClass() {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestPrimitive.java");
    PsiTestUtil.addSourceRoot(myModule, psiFile.getVirtualFile().getParent());
    myFixture.getEditor().getCaretModel().moveToOffset(((PsiJavaFile) psiFile).getClasses()[0].getTextOffset());

    myFixture.findSingleIntention("Annotate class 'Test' as @Deprecated");
    assertNotAvailable("Annotate class 'Test' as @NotNull");
    assertNotAvailable("Annotate class 'Test' as @Nullable");
  }

  private void assertNotAvailable(String hint) {
    List<IntentionAction> actions = myFixture.filterAvailableIntentions(hint);
    assertEmpty(actions);
  }

  public void testAnnotated() {
    PsiFile psiFile = myFixture.configureByFile("lib/p/TestAnnotated.java");
    PsiTestUtil.addSourceRoot(myModule, psiFile.getVirtualFile().getParent());
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();
    assertNotAvailable("Annotate method 'get' as @NotNull");
    assertNotAvailable("Annotate method 'get' as @Nullable");

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertFalse(deannotateFix.isAvailable(myProject, editor, file));
  }

  public void testDeannotation() {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation.java", "Annotate method 'get' as @NotNull", "Annotate method 'get' as @Nullable");
    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsDeannotation_after.xml", false);
  }

  public void testDeannotation1() {
    addDefaultLibrary();
    myFixture.configureByFiles("lib/p/TestPrimitive.java", "content/anno/p/annotations.xml");
    doDeannotate("lib/p/TestDeannotation1.java", "Annotate parameter 'ss' as @NotNull", "Annotate parameter 'ss' as @Nullable");
    myFixture.checkResultByFile("content/anno/p/annotations.xml", "content/anno/p/annotationsDeannotation1_after.xml", false);
  }

  private void doDeannotate(@NonNls final String testPath, String hint1, String hint2) {
    myFixture.configureByFile(testPath);
    final PsiFile file = myFixture.getFile();
    final Editor editor = myFixture.getEditor();

    assertNotAvailable(hint1);
    assertNotAvailable(hint2);

    final DeannotateIntentionAction deannotateFix = new DeannotateIntentionAction();
    assertTrue(deannotateFix.isAvailable(myProject, editor, file));

    final PsiModifierListOwner container = DeannotateIntentionAction.getContainer(editor, file);
    assertNotNull(container);
    startListening(container, AnnotationUtil.NOT_NULL, true);
    ExternalAnnotationsManager.getInstance(myProject).deannotate(container, AnnotationUtil.NOT_NULL);
    stopListeningAndCheckEvents();

    FileDocumentManager.getInstance().saveAllDocuments();

    IntentionAction fix = myFixture.findSingleIntention(hint1);
    assertNotNull(fix);

    fix = myFixture.findSingleIntention(hint2);
    assertNotNull(fix);

    assertFalse(deannotateFix.isAvailable(myProject, editor, file));
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
    ExternalAnnotationsManager.getInstance(myProject).annotateExternally(method, AnnotationUtil.NOT_NULL, myFixture.getFile(), null);
    stopListeningAndCheckEvents();

    startListening(method, AnnotationUtil.NOT_NULL, false);
    new WriteCommandAction(myProject){
      @Override
      protected void run(@NotNull final Result result) {
        ExternalAnnotationsManager.getInstance(myProject).editExternalAnnotation(method, AnnotationUtil.NOT_NULL, null);
      }
    }.execute();
    stopListeningAndCheckEvents();

    startListening(method, AnnotationUtil.NOT_NULL, false);
    new WriteCommandAction(myProject){
      @Override
      protected void run(@NotNull final Result result) {
        ExternalAnnotationsManager.getInstance(myProject).deannotate(method, AnnotationUtil.NOT_NULL);
      }
    }.execute();
    stopListeningAndCheckEvents();
  }

  public void testListenerNotifiedOnExternalChanges() {
    addDefaultLibrary();
    myFixture.configureByFiles("/content/anno/p/annotations.xml");
    myFixture.configureByFiles("lib/p/Test.java");

    ExternalAnnotationsManager.getInstance(myProject).findExternalAnnotation(getOwner(), AnnotationUtil.NOT_NULL); // force creating service

    startListeningForExternalChanges();
    new WriteCommandAction(myProject) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myFixture.getTempDirPath() + "/content/anno/p/annotations.xml");
        assert file != null;
        String newText = "  " + StreamUtil.readText(file.getInputStream(), "UTF-8") + "      ";
        FileUtil.writeToFile(VfsUtilCore.virtualToIoFile(file), newText);
        file.refresh(false, false);
      }
    }.execute();
    stopListeningAndCheckEvents();
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
