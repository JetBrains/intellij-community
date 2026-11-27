// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.ClsElementImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.impl.compiled.ClsModifierListImpl;
import com.intellij.psi.impl.compiled.ClsParameterImpl;
import com.intellij.psi.impl.compiled.ClsReferenceListImpl;
import com.intellij.psi.impl.source.tree.java.ClassElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.DecompilerPreset;
import org.jetbrains.java.decompiler.IdeaDecompilerSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ClsPsiTest {
  private static final String TEST_DATA_PATH = "/psi/cls/repo";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new DefaultLightProjectDescriptor());

  @TempDir
  private Path tempDir;
  private PsiClass myObjectClass;
  private GlobalSearchScope myScope;

  @BeforeEach
  void setUp() {
    myScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(projectFixture.get().getModule());
    myObjectClass = ReadAction.computeBlocking(() -> JavaPsiFacadeEx.getInstanceEx(projectFixture.get().getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, myScope));
    assertNotNull(myObjectClass);
  }

  @Test
  public void testClassFileUpdate() throws IOException {
    File testFile = IoTestUtil.createTestFile(tempDir.toFile(), "TestClass.class");
    File file1 = new File(JavaTestUtil.getJavaTestDataPath() + TEST_DATA_PATH + "/1_TestClass.class");
    FileUtil.copy(file1, testFile);
    VirtualFile vFile = StandardFileSystems.local().refreshAndFindFileByPath(testFile.getAbsolutePath());
    vFile.refresh(false, false);
    assertEquals(FileUtil.loadFileBytes(file1).length, vFile.contentsToByteArray().length);
    assertNotNull(vFile, testFile.getPath());
    Project project = projectFixture.get().getProject();
    PsiFile file = ReadAction.computeBlocking(() -> PsiManager.getInstance(project).findFile(vFile));
    assertNotNull(file);

    SmartPsiElementPointer<PsiClass> classPointer = ReadAction.computeBlocking(() -> {
      PsiClass aClass = ((PsiJavaFile)file).getClasses()[0];
      assertTrue(aClass.isValid());
      assertEquals(2, aClass.getFields().length);
      assertEquals("field11", aClass.getFields()[0].getName());
      assertEquals("field12", aClass.getFields()[1].getName());
      assertEquals(2, aClass.getMethods().length);
      assertEquals("TestClass", aClass.getMethods()[0].getName());
      assertEquals("method1", aClass.getMethods()[1].getName());
      return SmartPointerManager.createPointer(aClass);
    });
    VirtualFile virtualFile = file.getVirtualFile();

    File file2 = new File(JavaTestUtil.getJavaTestDataPath() + TEST_DATA_PATH + "/2_TestClass.class");
    FileUtil.copy(file2, testFile);
    assertTrue(testFile.setLastModified(System.currentTimeMillis() + 5000));
    vFile.refresh(false, false);

    ReadAction.runBlocking(() -> {
      assertFalse(file.isValid());
      PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      PsiClass aClass = ((PsiJavaFile)psiFile).getClasses()[0];

      PsiClass pointerElement = classPointer.getElement();
      assertTrue(pointerElement != null && aClass == pointerElement);

      assertTrue(aClass.isValid());
      assertEquals(1, aClass.getFields().length);
      assertEquals("field2", aClass.getFields()[0].getName());
      assertEquals(2, aClass.getMethods().length);
      assertEquals("TestClass", aClass.getMethods()[0].getName());
      assertEquals("method2", aClass.getMethods()[1].getName());
    });
  }

  @Test
  public void testFile() {
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = getFile("MyClass");
      assertTrue(file.isValid());
      assertEquals("pack", file.getPackageName());
      assertEquals(1, file.getClasses().length);
      file = getFile("MyEnum");
      assertEquals(LanguageLevel.JDK_1_5, file.getLanguageLevel());
    });
  }

  @Test
  public void testClassBasics() {
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = getFile("MyClass");
      PsiClass aClass = file.getClasses()[0];
      assertTrue(aClass.isValid());

      assertEquals(file, aClass.getParent());
      assertEquals(file, aClass.getContainingFile());
      assertEquals("MyClass", aClass.getName());
      assertEquals("pack.MyClass", aClass.getQualifiedName());
      assertFalse(aClass.isInterface());
      assertFalse(aClass.isDeprecated());
    });
  }

  @Test
  public void testViewProviderHasJavaLanguage() {
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = getFile("MyClass");
      assertEquals(JavaLanguage.INSTANCE, file.getLanguage());
      assertEquals(JavaLanguage.INSTANCE, file.getViewProvider().getBaseLanguage());
    });
  }

  @Test
  public void testClassMembers() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = getFile("MyClass").getClasses()[0];

      PsiClass[] inners = aClass.getInnerClasses();
      assertEquals(1, inners.length);
      assertEquals(aClass, inners[0].getParent());

      PsiField[] fields = aClass.getFields();
      assertEquals(2, fields.length);
      assertEquals(aClass, fields[0].getParent());

      PsiMethod[] methods = aClass.getMethods();
      assertEquals(3, methods.length);
      assertEquals(aClass, methods[0].getParent());

      assertNotNull(aClass.findFieldByName("field1", false));
    });
  }

  @Test
  public void testModifierList() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = getFile("MyClass").getClasses()[0];

      PsiModifierList modifierList = aClass.getModifierList();
      assertNotNull(modifierList);
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PUBLIC));
      assertFalse(modifierList.hasModifierProperty(PsiModifier.STATIC));
      assertEquals(modifierList.getParent(), aClass);

      PsiField field = aClass.getFields()[0];
      modifierList = field.getModifierList();
      assertNotNull(modifierList);
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
      assertEquals(modifierList.getParent(), field);

      PsiMethod method = aClass.getMethods()[0];
      modifierList = method.getModifierList();
      assertNotNull(modifierList);
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
      assertEquals(modifierList.getParent(), method);
    });
  }

  @Test
  public void testField() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = getFile("MyClass").getClasses()[0];
      PsiField field1 = aClass.getFields()[0], field2 = aClass.getFields()[1];

      assertEquals("field1", field1.getName());

      PsiType type1 = field1.getType();
      assertEquals(PsiTypes.intType(), type1);
      assertEquals("int", type1.getPresentableText());
      assertTrue(type1 instanceof PsiPrimitiveType);

      PsiType type2 = field2.getType();
      assertTrue(type2.equalsToText("java.lang.Object[]"));
      assertEquals("Object[]", type2.getPresentableText());
      assertFalse(type2 instanceof PsiPrimitiveType);
      assertTrue(type2 instanceof PsiArrayType);

      PsiType componentType = ((PsiArrayType)type2).getComponentType();
      assertTrue(componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT));
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT_SHORT, componentType.getPresentableText());
      assertFalse(componentType instanceof PsiPrimitiveType);
      assertFalse(componentType instanceof PsiArrayType);

      PsiLiteralExpression initializer = (PsiLiteralExpression)field1.getInitializer();
      assertNotNull(initializer);
      assertEquals("123", initializer.getText());
      assertEquals(123, initializer.getValue());
      assertEquals(PsiTypes.intType(), initializer.getType());

      assertFalse(field2.hasInitializer());
      assertNull(field2.getInitializer());
    });
  }

  @Test
  public void testMethod() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = getFile("MyClass").getClasses()[0];

      assertEquals("method1", aClass.getMethods()[0].getName());

      PsiMethod method1 = aClass.getMethods()[0];
      PsiTypeElement type1 = method1.getReturnTypeElement();
      assertNotNull(type1);
      assertEquals(method1, type1.getParent());
      assertEquals("void", type1.getText());
      assertTrue(type1.getType() instanceof PsiPrimitiveType);
      assertFalse(method1.isConstructor());

      PsiMethod method3 = aClass.getMethods()[2];
      assertNull(method3.getReturnType());
      assertTrue(method3.isConstructor());
    });
  }

  @Test
  public void testTypeReference() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = getFile("MyClass").getClasses()[0];

      PsiType type1 = aClass.getFields()[0].getType();
      assertNull(PsiUtil.resolveClassInType(type1));

      PsiType type2 = aClass.getFields()[1].getType();
      PsiType componentType = ((PsiArrayType)type2).getComponentType();
      assertTrue(componentType instanceof PsiClassType);
      assertTrue(componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT));
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT_SHORT, componentType.getPresentableText());

      PsiElement target = PsiUtil.resolveClassInType(type2);
      assertEquals(myObjectClass, target);
    });
  }

  @Test
  public void testReferenceLists() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = getFile("MyClass").getClasses()[0];

      PsiReferenceList extList = aClass.getExtendsList();
      assertNotNull(extList);
      PsiClassType[] refs = extList.getReferencedTypes();
      assertEquals(1, refs.length);
      assertEquals("ArrayList", refs[0].getPresentableText());
      assertEquals("java.util.ArrayList", refs[0].getCanonicalText());

      PsiReferenceList implList = aClass.getImplementsList();
      assertNotNull(implList);
      PsiClassType[] implRefs = implList.getReferencedTypes();
      assertEquals(1, implRefs.length);
      assertEquals("Cloneable", implRefs[0].getPresentableText());
      assertEquals("java.lang.Cloneable", implRefs[0].getCanonicalText());

      PsiReferenceList throwsList = aClass.getMethods()[0].getThrowsList();
      PsiClassType[] throwsRefs = throwsList.getReferencedTypes();
      assertEquals(2, throwsRefs.length);
      assertEquals("Exception", throwsRefs[0].getPresentableText());
      assertEquals("java.lang.Exception", throwsRefs[0].getCanonicalText());
      assertEquals("IOException", throwsRefs[1].getPresentableText());
      assertEquals("java.io.IOException", throwsRefs[1].getCanonicalText());
    });
  }

  @Test
  public void testParameters() {
    PsiParameter[] parameters = ReadAction.computeBlocking(() -> {
      PsiClass aClass = getFile("MyClass").getClasses()[0];

      PsiParameter[] params = aClass.getMethods()[0].getParameterList().getParameters();
      assertEquals(2, params.length);

      PsiType type1 = params[0].getType();
      assertEquals("int[]", type1.getPresentableText());
      assertTrue(type1.equalsToText("int[]"));
      assertTrue(type1 instanceof PsiArrayType);
      assertNull(PsiUtil.resolveClassInType(type1));

      PsiType type2 = params[1].getType();
      assertEquals("Object", type2.getPresentableText());
      assertTrue(type2.equalsToText(CommonClassNames.JAVA_LANG_OBJECT));
      assertFalse(type2 instanceof PsiArrayType);
      assertFalse(type2 instanceof PsiPrimitiveType);
      PsiClass target2 = PsiUtil.resolveClassInType(type2);
      assertEquals(myObjectClass, target2);

      assertNotNull(params[0].getModifierList());
      assertNotNull(params[1].getModifierList());
      return params;
    });

    Runnable checkNames = () -> {
      assertTrue(((ClsParameterImpl)parameters[0]).isAutoGeneratedName());
      assertTrue(((ClsParameterImpl)parameters[1]).isAutoGeneratedName());

      assertEquals("ints", parameters[0].getName());
      assertEquals("o", parameters[1].getName());
    };

    DumbModeTestUtils.runInDumbModeSynchronously(projectFixture.get().getProject(), () -> ReadAction.runBlocking(checkNames::run));
    ReadAction.runBlocking(checkNames::run);
  }

  @Test
  public void testNoMirrorNotSetWarnings() {
    ReadAction.runBlocking(() -> {
      List<String> warnings = new ArrayList<>();
      LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
        @Override
        public boolean processWarn(@NotNull String category, @NotNull String message, Throwable t) {
          warnings.add(message);
          return super.processWarn(category, message, t);
        }
      }, () -> {
        PsiClass aClass = getFile("Modifiers").getClasses()[0];
        PsiMethod[] methods = aClass.getMethods();
        PsiMethod constructor = Arrays.stream(methods)
          .filter(t -> t.isConstructor())
          .findFirst().get();
        ClsModifierListImpl modifierList = (ClsModifierListImpl)constructor.getModifierList();
        ClsReferenceListImpl throwsList = (ClsReferenceListImpl)constructor.getThrowsList();

        // We are actually most interested in the side effect of calls to getText() (we want to ensure no warnings are being logged)

        assertNotNull(BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> modifierList.getMirror()));
        assertEquals("public", BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> modifierList.getText()));
        assertNotNull(BinaryFileTypeDecompilers.getInstance()
                        .allowDecompilerSlowOperation(() -> modifierList.getMirror())); // assert that calling getText() does not set a mirror

        assertNotNull(BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> throwsList.getMirror()));
        assertEquals("", throwsList.getText());
        assertNotNull(BinaryFileTypeDecompilers.getInstance()
                        .allowDecompilerSlowOperation(() -> throwsList.getMirror())); // assert that calling getText() does not set a mirror
      });

      assertThat(warnings).isEmpty();
    });
  }

  @Test
  public void testModifiers_high() {
    ReadAction.runBlocking(() -> {
      final var originalState = IdeaDecompilerSettings.getInstance().getState();
      IdeaDecompilerSettings.State state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.HIGH);
      IdeaDecompilerSettings.getInstance().loadState(state);
      try {
        PsiClass aClass = getFile("Modifiers").getClasses()[0];
        assertEquals("private transient", aClass.getFields()[0].getModifierList().getText());
        assertEquals("private volatile", aClass.getFields()[1].getModifierList().getText());

        PsiMethod methodM1 = aClass.getMethods()[1];
        assertEquals("private", methodM1.getModifierList().getText());

        PsiMethod methodM2 = aClass.getMethods()[2];
        assertEquals("private synchronized", methodM2.getModifierList().getText());
      }
      finally {
        IdeaDecompilerSettings.getInstance().loadState(originalState);
      }
    });
  }

  @Test
  public void testModifiers_medium() {
    ReadAction.runBlocking(() -> {
      final var originalState = IdeaDecompilerSettings.getInstance().getState();
      IdeaDecompilerSettings.State state = IdeaDecompilerSettings.State.fromPreset(DecompilerPreset.MEDIUM);
      IdeaDecompilerSettings.getInstance().loadState(state);
      try {
        PsiClass aClass = getFile("Modifiers").getClasses()[0];
        assertEquals("private transient", aClass.getFields()[0].getModifierList().getText());
        assertEquals("private volatile", aClass.getFields()[1].getModifierList().getText());

        PsiMethod constructor = aClass.getMethods()[0];
        assertEquals("public", constructor.getModifierList().getText());

        PsiMethod methodM1 = aClass.getMethods()[1];
        assertEquals("private", methodM1.getModifierList().getText());

        PsiMethod methodM2 = aClass.getMethods()[2];
        assertEquals("private synchronized", methodM2.getModifierList().getText());
      }
      finally {
        IdeaDecompilerSettings.getInstance().loadState(originalState);
      }
    });
  }

  @Test
  public void testEnum() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = getFile("MyEnum").getClasses()[0];
      assertTrue(aClass.isEnum());
      PsiField[] fields = aClass.getFields();
      PsiClassType type = JavaPsiFacadeEx.getInstanceEx(projectFixture.get().getProject()).getElementFactory().createType(aClass);
      assertEnumConstant(fields[0], "RED", type);
      assertEnumConstant(fields[1], "GREEN", type);
      assertEnumConstant(fields[2], "BLUE", type);
    });
  }

  private static void assertEnumConstant(PsiField field, String name, PsiClassType type) {
    assertEquals(name, field.getName());
    assertTrue(field instanceof PsiEnumConstant);
    assertEquals(type, field.getType());
  }

  @Test
  public void testSyntheticEnumMethodsWhereApplicable() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = JavaPsiFacadeEx.getInstanceEx(projectFixture.get().getProject()).findClass(TimeUnit.class.getName());
      assertTrue(aClass.isEnum());
      assertEquals(1, aClass.findMethodsByName("valueOf", false).length);
      assertEquals(1, aClass.findMethodsByName("values", false).length);

      Collection<PsiClass> constants = DirectClassInheritorsSearch.search(aClass).findAll();
      assertEquals(7, constants.size());
      for (PsiClass constant : constants) {
        assertEquals(0, constant.findMethodsByName("valueOf", false).length);
        assertEquals(0, constant.findMethodsByName("values", false).length);
      }
    });
  }

  @Test
  public void testAnnotations() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = getFile("Annotated").getClasses()[0];

      PsiModifierList modifierList = aClass.getModifierList();
      assertNotNull(modifierList);
      checkAnnotations(modifierList);

      PsiMethod method = aClass.getMethods()[1];
      checkAnnotations(method.getModifierList());

      PsiParameter[] params = method.getParameterList().getParameters();
      assertEquals(1, params.length);
      modifierList = params[0].getModifierList();
      assertNotNull(modifierList);
      checkAnnotations(modifierList);

      modifierList = aClass.getFields()[0].getModifierList();
      assertNotNull(modifierList);
      checkAnnotations(modifierList);

      PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
      assertEquals(1, typeParameters.length);
      checkAnnotations(typeParameters[0]);
    });
  }

  private static void checkAnnotations(@NotNull PsiAnnotationOwner annotationOwner) {
    PsiAnnotation[] annotations = annotationOwner.getAnnotations();
    assertEquals(1, annotations.length);
    assertEquals("@pack.Annotation", annotations[0].getText());
    assertNotNull(annotationOwner.findAnnotation("pack.Annotation"));
    assertTrue(annotationOwner.hasAnnotation("pack.Annotation"));
  }

  @Test
  public void testAnnotationMethods() {
    ReadAction.runBlocking(() -> {
      PsiMethod[] methods = getFile("Annotation").getClasses()[0].getMethods();
      assertNotNull(((PsiAnnotationMethod)methods[0]).getDefaultValue());

      methods = getFile("Annotation2").getClasses()[0].getMethods();
      for (PsiMethod method : methods) {
        assertTrue(method instanceof PsiAnnotationMethod, String.valueOf(method));
        try {
          PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)method).getDefaultValue();
          assertTrue(defaultValue instanceof PsiBinaryExpression, String.valueOf(defaultValue));
          PsiPrimitiveType type = method.getName().startsWith("f") ? PsiTypes.floatType() : PsiTypes.doubleType();
          assertEquals(type, ((PsiBinaryExpression)defaultValue).getType());
        }
        catch (Exception e) {
          String valueText = ((ClsMethodImpl)method).getStub().getDefaultValueText();
          fail("Unable to compute default value of method " + method + " from text '" + valueText + "': " + e.getMessage());
        }
      }
    });
  }

  @Test
  public void testKeywordAnnotatedClass() {
    ReadAction.runBlocking(() -> {
      PsiModifierList modList = getFile("../KeywordAnnotatedClass").getClasses()[0].getModifierList();
      assertNotNull(modList);
      assertEquals("@pkg.native", modList.getAnnotations()[0].getText());
    });
  }

  @Test
  public void testGenerics() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = JavaPsiFacadeEx.getInstanceEx(projectFixture.get().getProject()).findClass(CommonClassNames.JAVA_UTIL_HASH_MAP, myScope);
      assertNotNull(aClass);

      PsiTypeParameter[] parameters = aClass.getTypeParameters();
      assertEquals(2, parameters.length);
      assertEquals("K", parameters[0].getName());
      assertEquals("V", parameters[1].getName());

      PsiClassType extType = aClass.getExtendsListTypes()[0];
      assertEquals("java.util.AbstractMap<K,V>", extType.getCanonicalText());

      PsiType returnType = aClass.findMethodsByName("entrySet", false)[0].getReturnType();
      assertNotNull(returnType);
      assertEquals("java.util.Set<java.util.Map.Entry<K,V>>", returnType.getCanonicalText());

      PsiType paramType = aClass.findMethodsByName("putAll", false)[0].getParameterList().getParameters()[0].getType();
      assertNotNull(paramType);
      assertEquals("java.util.Map<? extends K,? extends V>", paramType.getCanonicalText());
      assertNotNull(((PsiClassType)paramType).resolveGenerics().getElement());
    });
  }

  @Test
  public void testFqnCorrectness() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = getFile("$Weird$Name").getClasses()[0].getInnerClasses()[0];
      assertEquals("pack.$Weird$Name.Inner", aClass.getQualifiedName());
    });
  }

  @Test
  public void testFieldConstantValue() {
    ReadAction.runBlocking(() -> {
      PsiClass dbl = JavaPsiFacadeEx.getInstanceEx(projectFixture.get().getProject()).findClass(Double.class.getName(), myScope);
      assertNotNull(dbl);
      PsiField dnn = dbl.findFieldByName("NaN", false);
      assertNotNull(dnn);
      assertEquals(Double.NaN, dnn.computeConstantValue());

      PsiClass cls = getFile("../../mirror/pkg/Primitives").getClasses()[0];
      PsiField lng = cls.findFieldByName("LONG", false);
      assertNotNull(lng);
      assertEquals(42L, lng.computeConstantValue());
    });
  }

  @Test
  public void testAnnotationsOnTypes() {
    ReadAction.runBlocking(() -> {
      PsiClass cls = getFile("../../mirror/pkg/TypeAnnotations").getClasses()[0];

      PsiField f1 = cls.findFieldByName("f1", false);
      assertNotNull(f1);
      assertEquals("java.lang.@pkg.TypeAnnotations.TA(\"field type\") String", f1.getType().getCanonicalText(true));

      PsiField f2 = cls.findFieldByName("f2", false);
      assertNotNull(f2);
      assertEquals("java.lang.@pkg.TypeAnnotations.MixA(\"field and type\") String", f2.getType().getCanonicalText(true));

      PsiMethod m1 = cls.findMethodsByName("m1", false)[0];
      assertEquals("@pkg.TypeAnnotations.TA(\"return type\") int", Objects.requireNonNull(m1.getReturnType()).getCanonicalText(true));

      PsiParameter p1 = cls.findMethodsByName("m2", false)[0].getParameterList().getParameters()[0];
      assertEquals("@pkg.TypeAnnotations.TA(\"parameter\") int", p1.getType().getCanonicalText(true));
    });
  }

  @Test
  public void testModuleInfo() {
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = getFile("../../stubBuilder/module-info");
      assertEquals(LanguageLevel.JDK_1_9, file.getLanguageLevel());
      assertEquals("", file.getPackageName());

      PsiJavaModule module = file.getModuleDeclaration();
      assertNotNull(module);
      assertEquals("M.N", module.getName());

      assertNull(file.getPackageStatement());
      assertEquals(0, file.getClasses().length);
    });
  }

  private static PsiJavaFile getFile(String name) {
    String path = JavaTestUtil.getJavaTestDataPath() + TEST_DATA_PATH + "/pack/" + name + ".class";
    VirtualFile file = StandardFileSystems.local().refreshAndFindFileByPath(path);
    assertNotNull(file, path);
    PsiFile clsFile = PsiManager.getInstance(projectFixture.get().getProject()).findFile(file);
    assertTrue(clsFile instanceof ClsFileImpl, String.valueOf(clsFile));
    return (PsiJavaFile)clsFile;
  }

  @Test
  public void testClsPsiDoesNotHoldStrongReferencesToMirrorAST() {
    PsiClass dbl = ReadAction.computeBlocking(() -> {
      PsiClass found = JavaPsiFacadeEx.getInstanceEx(projectFixture.get().getProject()).findClass(Double.class.getName(), myScope);
      assertNotNull(found);
      assertEquals(found, BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> ((ClsClassImpl)found).getMirror())
        .getUserData(ClsElementImpl.COMPILED_ELEMENT));
      return found;
    });

    GCWatcher.tracking(ReadAction.computeBlocking(
      () -> BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> ((ClsClassImpl)dbl).getMirror()))).ensureCollected();
    LeakHunter.checkLeak(dbl, ClassElement.class, element -> element.getPsi().getUserData(ClsElementImpl.COMPILED_ELEMENT) == dbl);
  }

  @Test
  public void testMirrorBecomesInvalidTogetherWithCls() throws IOException {
    File testFile = IoTestUtil.createTestFile(tempDir.toFile(), "TestClass.class");
    File file1 = new File(JavaTestUtil.getJavaTestDataPath() + TEST_DATA_PATH + "/1_TestClass.class");
    FileUtil.copy(file1, testFile);
    VirtualFile copyVFile = StandardFileSystems.local().refreshAndFindFileByPath(testFile.getAbsolutePath());
    Project project = projectFixture.get().getProject();
    ClsFileImpl clsFile = ReadAction.computeBlocking(() -> (ClsFileImpl)PsiManager.getInstance(project).findFile(copyVFile));
    PsiElement mirror = ReadAction.computeBlocking(
      () -> BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> clsFile.getMirror()));
    ReadAction.runBlocking(() -> {
      assertTrue(clsFile.isValid());
      assertTrue(mirror.isValid());
    });

    WriteAction.runAndWait(() -> copyVFile.delete(this));
    ReadAction.runBlocking(() -> {
      assertFalse(clsFile.isValid());
      assertFalse(mirror.isValid());
      assertThat(PsiInvalidElementAccessException.findOutInvalidationReason(mirror)).contains(
        PsiInvalidElementAccessException.findOutInvalidationReason(clsFile));
    });
  }

  @Test
  public void testClsArrayTypeCreation() {
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = getFile("ClsContainer");
      PsiType type = file.getClasses()[0].getMethods()[3].getReturnType();
      TypeNullability nullability = type.getNullability();
      assertEquals(Nullability.NOT_NULL, nullability.nullability());
    });
  }
}
