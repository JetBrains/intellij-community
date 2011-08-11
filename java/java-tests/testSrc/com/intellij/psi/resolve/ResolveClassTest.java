package com.intellij.psi.resolve;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ResolveTestCase;

public class ResolveClassTest extends ResolveTestCase {
  public void testFQName() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testVarInNew() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testVarInNew1() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testPrivateInExtends() throws Exception{
    PsiReference ref = configure();
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertTrue(target instanceof PsiClass);
    assertFalse(result.isAccessible());
  }

  public void testQNew1() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testInnerPrivateMember1() throws Exception{
    PsiReference ref = configure();
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertTrue(target instanceof PsiClass);
    assertTrue(result.isValidResult());
  }


  public void testQNew2() throws Exception{
    PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)configure();
    PsiElement target = ref.advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);

    PsiElement parent = ref.getParent();
    assertTrue(parent instanceof PsiAnonymousClass);
    ((PsiAnonymousClass)parent).getBaseClassType().resolve();

    assertEquals(target, ((PsiAnonymousClass)parent).getBaseClassType().resolve());
  }

  public void testClassExtendsItsInner1() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    assertEquals("B.Foo", ((PsiClass)target).getQualifiedName());

    PsiReference refCopy = ref.getElement().copy().getReference();
    assert refCopy != null;
    PsiElement target1 = ((PsiJavaReference)refCopy).advancedResolve(true).getElement();
    assertTrue(target1 instanceof PsiClass);
    //assertNull(target1.getContainingFile().getVirtualFile());
    assertEquals("B.Foo", ((PsiClass)target1).getQualifiedName());
  }

  public void testClassExtendsItsInner2() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNull(target);  //[ven] this should not be resolved
    /*assertTrue(target instanceof PsiClass);
    assertEquals("TTT.Bar", ((PsiClass)target).getQualifiedName());*/
  }

   public void testSCR40332() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNull(target);
  }

  public void testImportConflict1() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target == null);
  }

  public void testImportConflict2() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    assertEquals("java.util.Date", ((PsiClass)target).getQualifiedName());
  }

  public void testLocals1() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    // local class
    assertNull(((PsiClass)target).getQualifiedName());
  }

  public void testLocals2() throws Exception{
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
    // local class
    assertNull(((PsiClass)target).getQualifiedName());
  }

  public void testShadowing() throws Exception {
    PsiReference ref = configure();
    JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    assertTrue(result.getElement() instanceof PsiClass);
    assertTrue(!result.isValidResult());
    assertTrue(!result.isAccessible());
  }

  public void testStaticImportVsImplicit() throws Exception {
    PsiReference ref = configure();
    JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    final PsiElement element = result.getElement();
    assertTrue(element instanceof PsiClass);
    assertTrue("Outer.Double".equals(((PsiClass)element).getQualifiedName()));
  }

  public void testTwoModules() throws Exception{
    configureDependency();
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertTrue(target instanceof PsiClass);
  }

  public void testTwoModules2() throws Exception{
    configureDependency();
    PsiReference ref = configure();
    PsiElement target = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    assertNull(target);
  }

  public void testStaticImportInTheSameClass() throws Exception {
    PsiReference ref = configure();
    long start = System.currentTimeMillis();
    assertNull(ref.resolve());
    PlatformTestUtil.assertTiming("exponent?", 20000, System.currentTimeMillis() - start);
  }

  public void testStaticImportNetwork() throws Exception {
    PsiReference ref = configure();
    int count = 20;

    String imports = "";
    for (int i = 0; i < count; i++) {
      imports += "import static Foo" + i + ".*;\n";
    }

    for (int i = 0; i < count; i++) {
      createFile(myModule, "Foo" + i + ".java", imports + "class Foo" + i + " extends Bar1, Bar2, Bar3 {}");
    }

    long start = System.currentTimeMillis();
    assertNull(ref.resolve());
    PlatformTestUtil.assertTiming("exponent?", 20000, System.currentTimeMillis() - start);
  }

  @SuppressWarnings({"ConstantConditions"})
  private void configureDependency() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ModifiableModuleModel modifiableModel = ModuleManager.getInstance(getProject()).getModifiableModel();
        Module module = modifiableModel.newModule("a.iml", StdModuleTypes.JAVA);
        modifiableModel.commit();

        ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(getTestDataPath() + "/class/dependentModule");
        assert root != null;

        ContentEntry contentEntry = rootModel.addContentEntry(root);
        contentEntry.addSourceFolder(root.findChild("src"), false);
        contentEntry.addSourceFolder(root.findChild("test"), true);
        rootModel.commit();

        ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
        modifiableRootModel.addModuleOrderEntry(module);
        modifiableRootModel.commit();
      }
    });
  }

  private PsiReference configure() throws Exception {
    return configureByFile("class/" + getTestName(false) + ".java");
  }
}
