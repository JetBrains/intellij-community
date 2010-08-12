package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.search.searches.AnnotatedPackagesSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author dsl
 */
public class Src15RepositoryUseTest extends PsiTestCase {

  public Src15RepositoryUseTest() {
    myRunCommandForTest = true;
  }

  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {

        public void run() {
          try {
            LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_5);
            String root = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/src15";
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17("mock 1.5"));
            PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17("mock 1.5");
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_5);
    super.tearDown();
  }


  public void testStaticImports() throws IOException {
    setupLoadingFilter();

    final PsiClass aClass = myJavaFacade.findClass("staticImports.StaticImports", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(aClass);
    final PsiJavaFile javaFile = (PsiJavaFile)aClass.getContainingFile();
    doTestStaticImports(javaFile, false);
    teardownLoadingFilter();
    doTestStaticImports(javaFile, true);
  }

  public void testDeprecatedAnnotation() throws IOException {
    setupLoadingFilter();

    final PsiClass aClass = myJavaFacade.findClass("annotations.DeprecatedAnnotation", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(aClass);
    assertTrue(aClass.isDeprecated());
    PsiMethod method = aClass.getMethods()[0];
    assertTrue(method.isDeprecated());
    PsiField field = aClass.getFields()[0];
    assertTrue(field.isDeprecated());
    teardownLoadingFilter();
  }

  public void testEnumImplements() {
    setupLoadingFilter();

    final PsiClass aClass = myJavaFacade.findClass("enumImplements.MyEnum", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(aClass);
    final PsiClassType[] implementsListTypes = aClass.getImplementsListTypes();
    assertEquals(1, implementsListTypes.length);

    final PsiClass baseInterface = implementsListTypes[0].resolve();
    assertNotNull(baseInterface);
    assertEquals("I", baseInterface.getName());
    teardownLoadingFilter();
  }

  private void doTestStaticImports(final PsiJavaFile javaFile, boolean okToLoadTree) {
    final PsiImportList importList = javaFile.getImportList();
    final PsiImportStatementBase[] allImportStatements = importList.getAllImportStatements();
    assertEquals(6, allImportStatements.length);
    final PsiImportStatement[] importStatements = importList.getImportStatements();
    assertEquals(2, importStatements.length);
    final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    assertEquals(4, importStaticStatements.length);
    assertFalse(importStatements[0].isOnDemand());
    assertTrue(importStaticStatements[0].isOnDemand());
    assertFalse(importStatements[0].isOnDemand());
    assertTrue(importStaticStatements[1].isOnDemand());
    assertFalse(importStaticStatements[2].isOnDemand());
    assertFalse(importStaticStatements[3].isOnDemand());

    assertEquals("sort", importStaticStatements[2].getReferenceName());
    assertEquals("sort", importStaticStatements[3].getReferenceName());

    final PsiImportStaticStatement classReference1 = importStaticStatements[1];
    final PsiElement element1 = classReference1.resolveTargetClass();
    assertNotNull(element1);
    assertTrue(element1 instanceof PsiClass);
    assertEquals("java.util.Arrays", ((PsiClass)element1).getQualifiedName());

    final PsiImportStaticStatement classReference3 = importStaticStatements[3];
    final PsiElement element3 = classReference3.resolveTargetClass();
    assertNotNull(element3);
    assertTrue(element3 instanceof PsiClass);
    assertEquals("java.util.Arrays", ((PsiClass)element3).getQualifiedName());

    if (okToLoadTree) {
      assertEquals("java.util.Collections", importStaticStatements[0].getImportReference().getText());
      assertEquals("java.util.Arrays", importStaticStatements[1].getImportReference().getText());
      assertEquals("java.util.Collections.sort", importStaticStatements[2].getImportReference().getText());
      assertEquals("java.util.Arrays.sort", importStaticStatements[3].getImportReference().getText());
    }
  }

  public void testEnum() throws Exception {
    setupLoadingFilter();
    final PsiClass enumClass = myJavaFacade.findClass("enums.OurEnum", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(enumClass);
    assertTrue(enumClass.isEnum());
    final PsiClass superClass = enumClass.getSuperClass();
    assertNotNull(superClass);
    assertEquals("java.lang.Enum", superClass.getQualifiedName());
    assertTrue(enumClass.isInheritor(superClass, false));
    final PsiClassType[] superTypes = enumClass.getSuperTypes();
    assertEquals(1, superTypes.length);
    assertEquals("java.lang.Enum<enums.OurEnum>", superTypes[0].getCanonicalText());
    final PsiClass[] supers = enumClass.getSupers();
    assertEquals(1, supers.length);
    assertEquals("java.lang.Enum", supers[0].getQualifiedName());
    final PsiClassType[] extendsListTypes = enumClass.getExtendsListTypes();
    assertEquals(1, extendsListTypes.length);
    assertEquals("java.lang.Enum<enums.OurEnum>", extendsListTypes[0].getCanonicalText());
    final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, enumClass, PsiSubstitutor.EMPTY);
    assertEquals("java.lang.Enum<enums.OurEnum>", myJavaFacade.getElementFactory().createType(superClass, superClassSubstitutor).getCanonicalText());
    teardownLoadingFilter();
  }

  public void testEnumWithConstants() throws Exception {
    setupLoadingFilter();
    PsiClass enumClass = myJavaFacade.findClass("enums.OurEnumWithConstants", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(enumClass);
    assertTrue(enumClass.isEnum());
    checkEnumWithConstants(enumClass, false);
    teardownLoadingFilter();
    checkEnumWithConstants(enumClass, true);
  }

  public void testEnumWithInitializedConstants() throws Exception {
    setupLoadingFilter();
    final GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(myModule);
    PsiClass enumClass = myJavaFacade.findClass("enums.OurEnumWithInitializedConstants", moduleScope);
    assertNotNull(enumClass);
    assertTrue(enumClass.isEnum());
    PsiField[] fields = enumClass.getFields();
    assertEquals(3, fields.length);
    assertTrue(fields[0] instanceof PsiEnumConstant);
    assertTrue(fields[1] instanceof PsiEnumConstant);
    assertTrue(fields[2] instanceof PsiEnumConstant);
    PsiAnonymousClass initializingClass0 = ((PsiEnumConstant)fields[0]).getInitializingClass();
    PsiClass baseClass0 = initializingClass0.getBaseClassType().resolve();
    assertTrue(baseClass0 == enumClass);

    PsiAnonymousClass initializingClass1 = ((PsiEnumConstant)fields[1]).getInitializingClass();
    PsiClass baseClass1 = initializingClass1.getBaseClassType().resolve();
    assertTrue(baseClass1 == enumClass);


    PsiAnonymousClass initializingClass2 = ((PsiEnumConstant)fields[1]).getInitializingClass();
    PsiClass baseClass2 = initializingClass2.getBaseClassType().resolve();
    assertTrue(baseClass2 == enumClass);

    assertTrue(initializingClass0.isInheritor(enumClass, false));
    assertTrue(initializingClass1.isInheritor(enumClass, false));
    assertTrue(initializingClass2.isInheritor(enumClass, false));

    final PsiClass[] enumInheritors = ClassInheritorsSearch.search(enumClass, moduleScope, false).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(3, enumInheritors.length);
    assertTrue(Arrays.asList(enumInheritors).contains(initializingClass0));
    assertTrue(Arrays.asList(enumInheritors).contains(initializingClass1));
    assertTrue(Arrays.asList(enumInheritors).contains(initializingClass2));


    PsiMethod[] methods1 = initializingClass2.getMethods();
    assertEquals(1, methods1.length);
    assertEquals("foo", methods1[0].getName());

    final PsiClass baseInterfaceClass = myJavaFacade.findClass("enums.OurBaseInterface", GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(baseInterfaceClass);

    final PsiClass[] inheritors = ClassInheritorsSearch.search(baseInterfaceClass, moduleScope, false).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(1, inheritors.length);
    assertTrue(inheritors[0] instanceof PsiAnonymousClass);

    teardownLoadingFilter();

    assertTrue(inheritors[0].getParent().getParent() instanceof PsiExpressionList);
    assertTrue(inheritors[0].getParent().getParent().getParent() == fields[2]);

    final PsiExpression[] expressions2 = ((PsiEnumConstant)fields[2]).getArgumentList().getExpressions();
    assertEquals(1, expressions2.length);
    assertTrue(expressions2[0] instanceof PsiNewExpression);
    final PsiAnonymousClass anonymousClass2 = ((PsiNewExpression)expressions2[0]).getAnonymousClass();
    assertTrue(anonymousClass2 != null);
    assertTrue(anonymousClass2.isInheritor(baseInterfaceClass, false));

  }

  private void checkEnumWithConstants(PsiClass enumClass, boolean okToLoadTree) {
    PsiField[] fields = enumClass.getFields();
    assertEquals(3, fields.length);
    checkEnumConstant("A", fields[0]);
    checkEnumConstant("B", fields[1]);
    checkEnumConstant("C", fields[2]);
    if (okToLoadTree) {
      assertEquals("A", fields[0].getText());
      assertEquals("B", fields[1].getText());
      assertEquals("C", fields[2].getText());
    }
  }

  private void checkEnumConstant(String expectedName, PsiField field) {
    assertTrue(field instanceof PsiEnumConstant);
    assertEquals(expectedName, field.getName());
    assertTrue(field.hasModifierProperty(PsiModifier.PUBLIC));
    assertTrue(field.hasModifierProperty(PsiModifier.FINAL));
    assertTrue(field.hasModifierProperty(PsiModifier.STATIC));
  }

  public void testEnumWithConstantsAndSastaticFields() throws Exception {
    setupLoadingFilter();
    PsiClass enumClass = myJavaFacade.findClass("enums.OurEnumWithConstantsAndStaticFields", GlobalSearchScope.moduleScope(myModule));
    PsiField[] fields = enumClass.getFields();
    assertTrue(fields[0] instanceof PsiEnumConstant);
    assertTrue(fields[1] instanceof PsiEnumConstant);
    assertTrue(fields[2] instanceof PsiEnumConstant);
    assertFalse(fields[3] instanceof PsiEnumConstant);

    teardownLoadingFilter();

    assertEquals("public static final int A1 = 1;", fields[3].getText());
  }

  public void testEnumWithConstantsAndStaticFields2() throws Exception {
    setupLoadingFilter();
    PsiClass enumClass = myJavaFacade.findClass("enums.OurEnumWithConstantsAndStaticFields2", GlobalSearchScope.moduleScope(myModule));
    PsiField[] fields = enumClass.getFields();
    assertTrue(fields[0] instanceof PsiEnumConstant);
    assertTrue(fields[1] instanceof PsiEnumConstant);
    assertTrue(fields[2] instanceof PsiEnumConstant);
    assertFalse(fields[3] instanceof PsiEnumConstant);

    teardownLoadingFilter();

    assertEquals("public static final int A1 = 10;", fields[3].getText());
    enumClass.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }
    });
    enumClass.getText();
  }

  private void teardownLoadingFilter() {
    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);
  }

  private void setupLoadingFilter() {
    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.ALL);
  }


  public void testAnnotationType() throws Exception {
    setupLoadingFilter();
    final PsiClass annotationTypeClass = myJavaFacade.findClass("annotations.AnnotationType", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(annotationTypeClass);
    assertTrue(annotationTypeClass.isAnnotationType());
    teardownLoadingFilter();
  }

  public void testAnnotationIndex() throws Exception {
    getJavaFacade().setAssertOnFileLoadingFilter(new VirtualFileFilter() {
      public boolean accept(final VirtualFile file) {
        return !"package-info.java".equals(file.getName());
      }
    });

    final PsiClass annotationTypeClass = myJavaFacade.findClass("annotations.AnnotationType", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(annotationTypeClass);
    assertTrue(annotationTypeClass.isAnnotationType());

    final Collection<PsiMember> all = AnnotatedMembersSearch.search(annotationTypeClass, GlobalSearchScope.moduleScope(myModule)).findAll();

    assertEquals(2, all.size());
    Set<String> correctNames = new HashSet<String>(Arrays.asList("AnnotatedClass", "correctMethod"));
    for (PsiMember member : all) {
      assertTrue(correctNames.contains(member.getName()));
    }

    final Collection<PsiPackage> packages =
      AnnotatedPackagesSearch.search(annotationTypeClass, GlobalSearchScope.moduleScope(myModule)).findAll();
    assertEquals(1, packages.size());
    assertEquals("annotated", packages.iterator().next().getQualifiedName());

    teardownLoadingFilter();
  }
}
