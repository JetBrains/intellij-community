package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class ClsRepositoryUseTest extends PsiTestCase{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.ClsRepositoryUseTest");
  private static final String TEST_ROOT = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/cls";
  private GlobalSearchScope RESOLVE_SCOPE;

  public ClsRepositoryUseTest() {
    myRunCommandForTest = true;
  }

  protected void setUp() throws Exception {
    super.setUp();

    RESOLVE_SCOPE = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule);

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            VirtualFile vDir = getRootFile();
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk17());
            addLibraryToRoots(vDir, OrderRootType.CLASSES);
//            PsiTestUtil.addSourceContentToRoots(myProject, vDir);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  protected void tearDown() throws Exception {
    RESOLVE_SCOPE = null;
    super.tearDown();
  }

  public void testClassFileUpdate() throws Exception {
    final File classes = createTempDir("classes");

    final File com = new File(classes, "com");
    com.mkdir();

    File dataPath = new File(PathManagerEx.getTestDataPath() + "/psi/cls");


    final File target = new File(com, "TestClass.class");
    FileUtil.copy(new File(dataPath, "1/TestClass.class"), target);
    target.setLastModified(System.currentTimeMillis());

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(classes);
            assertNotNull(vDir);
            addLibraryToRoots(vDir, OrderRootType.CLASSES);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );

    PsiClass psiClass = myJavaFacade.findClass("com.TestClass", GlobalSearchScope.allScope(myProject));
    PsiJavaFile psiFile = (PsiJavaFile)psiClass.getContainingFile();
    final VirtualFile vFile = psiFile.getVirtualFile();

    assertNotNull(psiClass);
    assertTrue(psiClass.isValid());
    assertTrue(psiFile.isValid());

    assertEquals(1, psiFile.getClasses().length);
    assertSame(psiClass, psiFile.getClasses()[0]);

    assertEquals(2, psiClass.getFields().length);
    assertEquals("field11", psiClass.getFields()[0].getName());
    assertEquals("field12", psiClass.getFields()[1].getName());

    assertEquals(2, psiClass.getMethods().length);
    assertEquals("TestClass", psiClass.getMethods()[0].getName());
    assertEquals("method1", psiClass.getMethods()[1].getName());

    FileUtil.copy(new File(dataPath, "2/TestClass.class"), target);
    target.setLastModified(System.currentTimeMillis() + 5000);

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            vFile.refresh(false, false);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );

    assertTrue(psiFile.isValid());
    PsiClass oldClass = psiClass;
    psiClass = myJavaFacade.findClass("com.TestClass", GlobalSearchScope.allScope(myProject));
    assertNotSame(oldClass, psiClass);
    assertFalse(oldClass.isValid());

    assertNotNull(psiClass);
    assertEquals(1, psiFile.getClasses().length);
    assertSame(psiClass, psiFile.getClasses()[0]);

    assertTrue(psiClass.isValid());
    assertEquals(1, psiClass.getFields().length);
    assertEquals("field2", psiClass.getFields()[0].getName());

    assertEquals(2, psiClass.getMethods().length);
    assertEquals("TestClass", psiClass.getMethods()[0].getName());
    assertEquals("method2", psiClass.getMethods()[1].getName());
  }


  private static VirtualFile getRootFile() {
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByPath(TEST_ROOT.replace(File.separatorChar, '/'));
    return vDir;
  }

  public void testGetClasses(){
    final VirtualFile rootFile = getRootFile();
    VirtualFile child = rootFile.findChild("pack").findChild("MyClass.class");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    checkValid(file);
    PsiClass[] classes = file.getClasses();
    assertTrue(classes.length == 1);
    PsiClass aClass = classes[0];
    checkValid(aClass);
    assertEquals(file, aClass.getParent());
  }

  public void testGetClassName(){
    final VirtualFile rootFile = getRootFile();
    VirtualFile child = rootFile.findChild("pack").findChild("MyClass.class");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertTrue(classes.length == 1);

    PsiClass aClass = classes[0];
    checkValid(aClass);
    assertEquals("MyClass", aClass.getName());
  }

  public void testGetClassQName(){
    final VirtualFile rootFile = getRootFile();
    VirtualFile child = rootFile.findChild("pack").findChild("MyClass.class");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertTrue(classes.length == 1);

    PsiClass aClass = classes[0];
    checkValid(aClass);
    assertEquals("pack.MyClass", aClass.getQualifiedName());
  }

  public void testGetContainingFile(){
    final VirtualFile rootFile = getRootFile();
    VirtualFile child = rootFile.findChild("pack").findChild("MyClass.class");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertTrue(classes.length == 1);

    PsiClass aClass = classes[0];
    checkValid(aClass);
    assertEquals(file, aClass.getContainingFile());
  }

  public void testFindClass(){
    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.ALL);

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);
    checkValid(aClass);

    getJavaFacade().setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);
  }

  public void testIsInterface(){
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    assertTrue(!aClass.isInterface());
  }

  private static void checkValid(final PsiElement elt) {
    assertTrue(elt.isValid());
  }

  public void testPackageName(){
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    String packageName = ((PsiJavaFile)aClass.getContainingFile()).getPackageName();
    assertEquals("pack", packageName);
  }

  public void testGetFields(){
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    PsiField[] fields = aClass.getFields();
    assertEquals(2, fields.length);
    assertEquals(aClass, fields[0].getParent());
  }

  public void testGetMethods(){
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    PsiMethod[] methods = aClass.getMethods();
    assertEquals(3, methods.length);
    assertEquals(aClass, methods[0].getParent());
  }

  public void testGetInnerClasses(){
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    PsiClass[] inners = aClass.getInnerClasses();
    assertEquals(1, inners.length);
    assertEquals(aClass, inners[0].getParent());
  }

  public void testModifierList() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    PsiModifierList modifierList = aClass.getModifierList();
    assertTrue(modifierList.hasModifierProperty(PsiModifier.PUBLIC));
    assertTrue(modifierList.hasModifierProperty(PsiModifier.STATIC));
    assertEquals(modifierList.getParent(), aClass);

    PsiField field = aClass.getFields()[0];
    modifierList = field.getModifierList();
    assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
    assertEquals(modifierList.getParent(), field);

    PsiMethod method = aClass.getMethods()[0];
    modifierList = method.getModifierList();
    assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
    assertEquals(modifierList.getParent(), method);
  }

  public void testGetFieldName(){
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    assertEquals("field1", aClass.getFields()[0].getName());
  }

  public void testGetMethodName(){
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    assertEquals("method1", aClass.getMethods()[0].getName());
  }

  public void testFindFieldByName(){
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    PsiField field = aClass.findFieldByName("field1", false);
    assertNotNull(field);
  }

  public void testIsDeprecated(){
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);
    assertTrue(!aClass.isDeprecated());

    PsiField field = aClass.findFieldByName("field1", false);
    assertTrue(field.isDeprecated());
  }

  public void testFieldType() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiField field1 = aClass.getFields()[0];
    PsiType type1 = field1.getType();

    PsiField field2 = aClass.getFields()[1];
    PsiType type2 = field2.getType();

    assertEquals(PsiType.INT, type1);
    assertTrue(type2.equalsToText("java.lang.Object[]"));

    assertEquals("int", type1.getPresentableText());
    assertEquals("Object[]", type2.getPresentableText());

    assertTrue(type1 instanceof PsiPrimitiveType);
    assertTrue(!(type1 instanceof PsiArrayType));
    assertTrue(!(type2 instanceof PsiPrimitiveType));
    assertTrue(type2 instanceof PsiArrayType);
  }

  public void testMethodType() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiMethod method1 = aClass.getMethods()[0];
    PsiTypeElement type1 = method1.getReturnTypeElement();
    assertEquals(method1, type1.getParent());

    assertEquals("void", type1.getText());
    assertTrue(type1.getType() instanceof PsiPrimitiveType);
    assertTrue(!(type1.getType() instanceof PsiArrayType));

    PsiMethod method3 = aClass.getMethods()[2];
    assertNull(method3.getReturnType());
  }

  public void testIsConstructor() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiMethod method1 = aClass.getMethods()[0];
    assertFalse(method1.isConstructor());

    PsiMethod method3 = aClass.getMethods()[2];
    assertTrue(method3.isConstructor());
  }

  public void testComponentType() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiField field = aClass.getFields()[1];
    PsiType type = field.getType();
    LOG.assertTrue(type instanceof PsiArrayType);
    PsiType componentType = ((PsiArrayType) type).getComponentType();

    assertTrue(componentType.equalsToText("java.lang.Object"));
    assertEquals("Object", componentType.getPresentableText());
    assertFalse(componentType instanceof PsiPrimitiveType);
    assertFalse(componentType instanceof PsiArrayType);
  }

  public void testTypeReference() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiField field1 = aClass.getFields()[0];
    PsiType type1 = field1.getType();
    assertNull(PsiUtil.resolveClassInType(type1));

    PsiField field2 = aClass.getFields()[1];
    PsiType type2 = ((PsiArrayType) field2.getType()).getComponentType();
    assertTrue(type2 instanceof PsiClassType);

    assertTrue(type2.equalsToText("java.lang.Object"));
    assertEquals("Object", type2.getPresentableText());
  }

  public void testResolveTypeReference() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiType type1 = aClass.getFields()[1].getType();
    PsiElement target1 = PsiUtil.resolveClassInType(type1);
    assertNotNull(target1);
    PsiClass objectClass = myJavaFacade.findClass("java.lang.Object", RESOLVE_SCOPE);
    assertEquals(objectClass, target1);
  }

  public void testInitializer() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiField field1 = aClass.getFields()[0];
    assertTrue(field1.hasInitializer());
    PsiLiteralExpression initializer = (PsiLiteralExpression)field1.getInitializer();
    assertEquals("123", initializer.getText());
    assertEquals(new Integer(123), initializer.getValue());
    assertEquals(PsiType.INT, initializer.getType());

    PsiField field2 = aClass.getFields()[1];
    assertFalse(field2.hasInitializer());
    assertNull(field2.getInitializer());
  }

  public void testExtendsList() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiReferenceList list = aClass.getExtendsList();

    PsiClassType[] refs = list.getReferencedTypes();
    assertEquals(1, refs.length);
    assertEquals("ArrayList", refs[0].getPresentableText());
    assertEquals("java.util.ArrayList", refs[0].getCanonicalText());
  }

  public void testImplementsList() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiReferenceList list = aClass.getImplementsList();

    PsiClassType[] refs = list.getReferencedTypes();
    assertEquals(1, refs.length);
    assertEquals("Cloneable", refs[0].getPresentableText());
    assertEquals("java.lang.Cloneable", refs[0].getCanonicalText());
  }

  public void testThrowsList() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiReferenceList list = aClass.getMethods()[0].getThrowsList();

    PsiClassType[] refs = list.getReferencedTypes();
    assertEquals(2, refs.length);
    assertEquals("Exception", refs[0].getPresentableText());
    assertEquals("IOException", refs[1].getPresentableText());
    assertEquals("java.lang.Exception", refs[0].getCanonicalText());
    assertEquals("java.io.IOException", refs[1].getCanonicalText());
  }


  public void testParameters() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiParameterList list = aClass.getMethods()[0].getParameterList();

    PsiParameter[] parms = list.getParameters();
    assertEquals(2, parms.length);

    PsiType type1 = parms[0].getType();
    assertEquals("int[]", type1.getPresentableText());
    assertTrue(type1.equalsToText("int[]"));
    assertTrue(type1 instanceof PsiArrayType);
    assertFalse(type1 instanceof PsiPrimitiveType);
    assertNull(PsiUtil.resolveClassInType(type1));

    PsiType type2 = parms[1].getType();
    assertEquals("Object", type2.getPresentableText());
    assertTrue(type2.equalsToText("java.lang.Object"));
    assertFalse(type2 instanceof PsiArrayType);
    assertFalse(type2 instanceof PsiPrimitiveType);
    PsiClass target2 = PsiUtil.resolveClassInType(type2);
    assertNotNull(target2);
    PsiClass objectClass = myJavaFacade.findClass("java.lang.Object", RESOLVE_SCOPE);
    assertEquals(objectClass, target2);

    parms[0].getModifierList();
  }

  public void testGenericClass() throws Exception {
    disableJdk();

    PsiClass map = myJavaFacade.findClass("java.util.HashMap", RESOLVE_SCOPE);
    PsiMethod entrySet = map.findMethodsByName("entrySet", false)[0];
    PsiClassType ret = (PsiClassType) entrySet.getReturnType();
    final PsiClassType.ClassResolveResult setResolveResult = ret.resolveGenerics();
    assertEquals("java.util.Set", setResolveResult.getElement().getQualifiedName());
    final PsiTypeParameter typeParameter = setResolveResult.getElement().getTypeParameters()[0];

    final PsiType substResult = setResolveResult.getSubstitutor().substitute(typeParameter);
    assertTrue(substResult instanceof PsiWildcardType);
    assertTrue(((PsiWildcardType)substResult).isExtends());
    PsiClassType setType = (PsiClassType)((PsiWildcardType)substResult).getBound();
    final PsiClassType.ClassResolveResult setTypeResolveResult = setType.resolveGenerics();
    assertEquals("java.util.Map.Entry", setTypeResolveResult.getElement().getQualifiedName());
    final PsiTypeParameter[] typeParameters = setTypeResolveResult.getElement().getTypeParameters();
    assertEquals(2, typeParameters.length);
    PsiType[] mapParams = new PsiType[]{
      setTypeResolveResult.getSubstitutor().substitute(typeParameters[0]),
      setTypeResolveResult.getSubstitutor().substitute(typeParameters[1])
    };
    assertEquals(2, mapParams.length);
    assertEquals("K", mapParams[0].getCanonicalText());
    assertTrue(((PsiClassType) mapParams[0]).resolve() instanceof PsiTypeParameter);
    assertEquals("V", mapParams[1].getCanonicalText());
    assertTrue(((PsiClassType) mapParams[1]).resolve() instanceof PsiTypeParameter);
  }

  private void disableJdk() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
        rootModel.setSdk(null);
        rootModel.commit();
      }
    }
    );
  }


  public void testGenericReturnType() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
        rootModel.setSdk(null);
        rootModel.commit();
      }
    }
    );

    final PsiClass map = myJavaFacade.findClass("java.util.Map", RESOLVE_SCOPE);
    final PsiElementFactory factory = myJavaFacade.getElementFactory();
    final PsiClassType typeMapStringToInteger =
            (PsiClassType) factory.createTypeFromText("java.util.Map <java.lang.String,java.lang.Integer>", null);
    final PsiClassType.ClassResolveResult mapResolveResult = typeMapStringToInteger.resolveGenerics();
    assertTrue(mapResolveResult.getElement().equals(map));

    final PsiMethod entrySetMethod = map.findMethodsByName("entrySet", false)[0];
    final PsiType entrySetReturnType = entrySetMethod.getReturnType();
    assertEquals("java.util.Set<? extends java.util.Map.Entry<K,V>>", entrySetReturnType.getCanonicalText());
    final PsiSubstitutor substitutor = ((PsiClassType)entrySetReturnType).resolveGenerics().getSubstitutor();
    assertEquals("E of java.util.Set -> ? extends java.util.Map.Entry<K,V>\n", substitutor.toString());
    final PsiType typeSetOfEntriesOfStringAndInteger = factory.createTypeFromText("java.util.Set<? extends java.util.Map.Entry<java.lang.String,java.lang.Integer>>", null);
    final PsiType substitutedEntrySetReturnType = mapResolveResult.getSubstitutor().substitute(entrySetReturnType);
    assertTrue(typeSetOfEntriesOfStringAndInteger.equals(substitutedEntrySetReturnType));
    assertTrue(typeSetOfEntriesOfStringAndInteger.isAssignableFrom(substitutedEntrySetReturnType));
  }

  public void testGenericInheritance() throws Exception {
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("Dummy.java", "import java.util.*;\n" +
                                                                                                         "class Dummy {\n" +
                                                                                                         "{ Map<Integer, Integer> list = new HashMap<Integer, Integer>();}\n" +
                                                                                                         "}");

    PsiDeclarationStatement decl = (PsiDeclarationStatement) file.getClasses()[0].getInitializers()[0].getBody().getStatements()[0];
    PsiVariable list = (PsiVariable) decl.getDeclaredElements()[0];

    assertTrue(list.getType().isAssignableFrom(list.getInitializer().getType()));
  }

  public void testSimplerGenericInheritance() throws Exception {
    PsiElementFactory factory = myJavaFacade.getElementFactory();
    PsiClass map = myJavaFacade.findClass("java.util.Map", RESOLVE_SCOPE);
    PsiClass hashMap = myJavaFacade.findClass("java.util.HashMap", RESOLVE_SCOPE);

    assertTrue(factory.createType(map).isAssignableFrom(factory.createType(hashMap)));
  }

  public void testAnnotations () throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.Annotated", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiAnnotation[] annotations = aClass.getModifierList().getAnnotations();
    assertTrue(annotations.length == 1);
    assertTrue(annotations[0].getText().equals("@pack.Annotation"));

    PsiMethod[] methods = aClass.findMethodsByName("foo", false);
    assertTrue(methods.length == 1);
    annotations = methods[0].getModifierList().getAnnotations();
    assertTrue(annotations.length == 1);
    assertTrue(annotations[0].getText().equals("@pack.Annotation"));

    PsiParameter[] params = methods[0].getParameterList().getParameters();
    assertTrue(params.length == 1);
    annotations = params[0].getModifierList().getAnnotations();
    assertTrue(annotations.length == 1);
    assertTrue(annotations[0].getText().equals("@pack.Annotation"));

    PsiField[] fields = aClass.getFields();
    assertTrue(fields.length == 1);
    annotations = fields[0].getModifierList().getAnnotations();
    assertTrue(annotations.length == 1);
    assertTrue(annotations[0].getText().equals("@pack.Annotation"));
  }

  public void testAnnotationMethodDefault () throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.Annotation", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    PsiMethod[] methods = aClass.findMethodsByName("value", false);
    assertTrue(methods.length == 1 && methods[0] instanceof PsiAnnotationMethod);
    PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)methods[0]).getDefaultValue();
    assertTrue(defaultValue != null);
  }

  public void testEnum() throws Exception {
    PsiClass aClass = myJavaFacade.findClass("pack.MyEnum", GlobalSearchScope.allScope(myProject));
    checkValid(aClass);

    assertTrue(aClass.isEnum());
    PsiField[] fields = aClass.getFields();
    PsiClassType type = myJavaFacade.getElementFactory().createType(aClass);
    checkEnumConstant("RED", fields[0], type);
    checkEnumConstant("GREEN", fields[1], type);
    checkEnumConstant("BLUE", fields[2], type);
  }

  public void testVariance() throws Exception {
    final PsiClass aClass = myJavaFacade.findClass("pack.Variance", RESOLVE_SCOPE);
    checkValid(aClass);
    final PsiMethod[] methodsByName = aClass.findMethodsByName("method", false);
    assertEquals(1, methodsByName.length);
    final PsiMethod methodsWithReturnType = methodsByName[0];
    final PsiType returnType = methodsWithReturnType.getReturnType();
    assertEquals("pack.Parametrized<? extends T>", returnType.getCanonicalText());

    //TODO[ven, max]: After fix for loading decompiled stuff the result had been change. Need to discuss whether this is important
    //enough to try to conform old output.
    //assertEquals("public Parametrized<? extends T> method() { /* compiled code */ }", methodsWithReturnType.getText());
    assertEquals("public pack.Parametrized<? extends T> method() { /* compiled code */ }", methodsWithReturnType.getText());
  }

  private void checkEnumConstant(String name, PsiField field, PsiClassType type) {
    assertEquals(name, field.getName());
    assertTrue(field instanceof PsiEnumConstant);
    assertEquals(type, field.getType());
  }

  public void testInvariantParsing() throws Exception {
    final PsiClass collection = myJavaFacade.findClass("pack.Variance", RESOLVE_SCOPE);
    assertNotNull(collection);
    final PsiMethod[] methods = collection.findMethodsByName("removeAll", false);
    assertEquals(1, methods.length);
    final PsiParameter[] parameters = methods[0].getParameterList().getParameters();
    assertEquals(1, parameters.length);
    final PsiType parameterType = parameters[0].getType();
    assertTrue(parameterType instanceof PsiClassType);
    final PsiClassType psiClassType = ((PsiClassType)parameterType);
    final PsiClassType.ClassResolveResult resolveResult = psiClassType.resolveGenerics();
    final PsiTypeParameter[] typeParameters = resolveResult.getElement().getTypeParameters();
    assertEquals(1, typeParameters.length);
    final PsiType substitution = resolveResult.getSubstitutor().substitute(typeParameters[0]);
    assertTrue(substitution instanceof PsiWildcardType);
    assertEquals(PsiWildcardType.createUnbounded(myPsiManager), substitution);
  }

  public void testInvariantParsing2() throws Exception {
    disableJdk();
    final PsiClass collection = myJavaFacade.findClass("java.util.Collection", RESOLVE_SCOPE);
    assertNotNull(collection);
    final PsiMethod[] methods = collection.findMethodsByName("removeAll", false);
    assertEquals(1, methods.length);
    final PsiParameter[] parameters = methods[0].getParameterList().getParameters();
    assertEquals(1, parameters.length);
    final PsiType parameterType = parameters[0].getType();
    assertTrue(parameterType instanceof PsiClassType);
    final PsiClassType psiClassType = ((PsiClassType)parameterType);
    final PsiClassType.ClassResolveResult resolveResult = psiClassType.resolveGenerics();
    final PsiTypeParameter[] typeParameters = resolveResult.getElement().getTypeParameters();
    assertEquals(1, typeParameters.length);
    final PsiType substitution = resolveResult.getSubstitutor().substitute(typeParameters[0]);
    assertTrue(substitution instanceof PsiWildcardType);
    assertEquals(PsiWildcardType.createUnbounded(myPsiManager), substitution);
  }

}
