// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveInLibrariesTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture;

  ResolveInLibrariesTest(TestInfo testInfo) {
    // The temp directory of testDoNotBuildStubsInSourceJars is no source root, as on JUnit 3.
    boolean addSourceRoot = !testInfo.getTestMethod().orElseThrow().getName().equals("testDoNotBuildStubsInSourceJars");
    codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath(), addSourceRoot);
  }

  @Test
  public void testInheritanceTransitivity() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile protobufJar =
      IntelliJProjectConfiguration.getVirtualFile(IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.protobuf", "protobuf"));
    VirtualFile jarCopy = WriteAction.computeAndWait(() -> JarFileSystem.getInstance().getLocalByEntry(protobufJar)
      .copy(this, myFixture.getTempDirFixture().findOrCreateDir("lib"), "protoJar.jar"));

    PsiTestUtil.addProjectLibrary(myFixture.getModule(), "proto1", List.of(protobufJar), List.of());
    PsiTestUtil.addProjectLibrary(myFixture.getModule(), "proto2", List.of(JarFileSystem.getInstance().getJarRootForLocalFile(jarCopy)), List.of());

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());

    ReadAction.runBlocking(() -> {
      PsiClass[] bottoms = JavaPsiFacade.getInstance(myFixture.getProject()).findClasses("com.google.protobuf.AbstractMessage", scope);
      assertEquals(2, bottoms.length);

      PsiClass[] middles = JavaPsiFacade.getInstance(myFixture.getProject()).findClasses("com.google.protobuf.AbstractMessageLite", scope);
      assertEquals(2, middles.length);

      PsiClass[] interfaces = JavaPsiFacade.getInstance(myFixture.getProject()).findClasses("com.google.protobuf.MessageLite", scope);
      assertEquals(2, interfaces.length);

      for (int i = 0; i < 2; i++) {
        assertTrue(ClassInheritorsSearch.search(interfaces[i]).findAll().containsAll(List.of(middles[i], bottoms[i])));
        for (PsiMethod method : interfaces[i].getMethods()) {
          assertFalse(OverridingMethodsSearch.search(method).findAll().isEmpty());
        }

        assertTrue(middles[i].isInheritor(interfaces[i], true));
        assertTrue(bottoms[i].isInheritor(interfaces[i], true));
        assertTrue(bottoms[i].isInheritor(middles[i], true));
      }
    });
  }

  @Test
  public void testMissedLibraryDependency() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myFixture.getProject());
    final Sdk oldSdk = projectRootManager.getProjectSdk();
    try {
      WriteAction.runAndWait(() -> setProjectSdk(projectRootManager, IdeaTestUtil.getMockJdk17()));

      // add jar with org.codehaus.groovy.ant.Groovydoc
      ModuleRootModificationUtil.updateModel(myFixture.getModule(), model -> 
        MavenDependencyUtil.addFromMaven(model, "org.codehaus.groovy:groovy-ant:2.4.17", false));

      // add jar with org.apache.tools.ant.Task which is the superclass of org.codehaus.groovy.ant.Groovydoc
      List<String> ant = IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("Ant");
      PsiTestUtil.addProjectLibrary(myFixture.getModule(), "ant", ant);

      myFixture.configureByText("Foo.java", """

        class Foo {\s
          {new org.codehaus.groovy.ant.Groovydoc().set<caret>Project(new org.apache.tools.ant.Project());}
        }""");

      ReadAction.runBlocking(() -> assertNotNull(myFixture.getElementAtCaret()));
    }
    finally {
      WriteAction.runAndWait(() -> projectRootManager.setProjectSdk(oldSdk));
    }
  }

  @Test
  public void testAcceptThatWithDifferentLibraryVersionsInheritanceRelationMayBeIntransitive() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile lib = StandardFileSystems.local().refreshAndFindFileByPath(JavaTestUtil.getJavaTestDataPath() + "/libResolve/inheritance");

    //Foo, Middle implements Foo, Other extends Middle
    PsiTestUtil.addLibrary(myFixture.getModule(), "full", lib.getPath(), new String[]{"/fullLibrary.jar!/"}, ArrayUtil.EMPTY_STRING_ARRAY);

    //Middle, Bottom extends Middle
    PsiTestUtil.addLibrary(myFixture.getModule(), "partial", lib.getPath(), new String[]{"/middleBottom.jar!/"}, ArrayUtil.EMPTY_STRING_ARRAY);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());

    ReadAction.runBlocking(() -> {
      PsiClass i0 = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("Intf", scope);
      PsiClass other0 = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("Other", scope);
      PsiClass b1 = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("Bottom", scope);

      PsiClass[] middles = JavaPsiFacade.getInstance(myFixture.getProject()).findClasses("Middle", scope);
      assertEquals(2, middles.length);
      PsiClass m0 = middles[0];
      PsiClass m1 = middles[1];

      for (boolean deep : new boolean[] {false, true}) {
        assertTrue(m0.isInheritor(i0, deep));
        assertTrue(other0.isInheritor(m0, deep));

        assertFalse(b1.isInheritor(i0, deep));

        assertTrue(b1.isInheritor(m0, deep));
        assertTrue(b1.isInheritor(m1, deep));

        assertFalse(m1.isInheritor(i0, deep));
      }


      assertTrue(other0.isInheritor(i0, true));
      assertFalse(other0.isInheritor(i0, false));

      assertEquals(ContainerUtil.newHashSet(m0, b1, other0),
                   new HashSet<>(ClassInheritorsSearch.search(i0).findAll()));
      assertEquals(ContainerUtil.newHashSet(b1, other0),
                   new HashSet<>(ClassInheritorsSearch.search(m0).findAll()));

      assertEquals(ContainerUtil.newHashSet(fooMethod(m0), fooMethod(other0)), fooInheritors(i0));
      assertEquals(ContainerUtil.newHashSet(fooMethod(other0), fooMethod(b1)), fooInheritors(m0));
      assertEquals(ContainerUtil.newHashSet(fooMethod(other0), fooMethod(b1)), fooInheritors(m1));
    });
  }

  private static PsiMethod fooMethod(PsiClass c) { 
    return c.findMethodsByName("foo", false)[0]; 
  }

  private static Set<PsiMethod> fooInheritors(PsiClass c) {
    return new HashSet<>(ReadAction.computeBlocking(() -> OverridingMethodsSearch.search(fooMethod(c)).findAll()));
  }

  @Test
  public void testDoNotParseNotStubbedSourcesInClassJars() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile lib =
      StandardFileSystems.local().refreshAndFindFileByPath(JavaTestUtil.getJavaTestDataPath() + "/libResolve/classesAndSources");
    PsiTestUtil.addLibrary(myFixture.getModule(), "cas", lib.getPath(), new String[]{"/classesAndSources.jar!/"},
                           new String[]{"/classesAndSources.jar!/"});

    JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());

    ReadAction.runBlocking(() -> {
      assertEquals(1, facade.findClasses("LibraryClass", scope).length);

      PsiPackage pkg = facade.findPackage("");
      assertEquals(1, pkg.getClasses().length);

      VirtualFile javaSrc = Stream.of(pkg.getDirectories())
        .flatMap(directory -> Stream.of(directory.getVirtualFile().getChildren()))
        .filter(file -> file.getName().equals("LibraryClass.java"))
        .findFirst()
        .orElseThrow();
      checkFileIsNotLoadedAndHasNoIndexedStub(javaSrc);

      assertTrue(pkg.containsClassNamed("LibraryClass"));
      checkFileIsNotLoadedAndHasNoIndexedStub(javaSrc);
      assertNull(((PsiFileImpl)PsiManager.getInstance(myFixture.getProject()).findFile(javaSrc)).getTreeElement());
    });
  }

  @Test
  public void testDoNotBuildStubsInSourceJars() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());

    String testDataPathForTest = JavaTestUtil.getJavaTestDataPath() + "/libResolve/classesAndSources";
    VirtualFile lib = StandardFileSystems.local().refreshAndFindFileByPath(testDataPathForTest);
    VirtualFile localFile = myFixture.copyFileToProject(testDataPathForTest + File.separator + "Foo.java", "Foo.java");
    assertNotNull(localFile);

    checkFileIsNotLoadedAndHasNoIndexedStub(localFile);
    ReadAction.runBlocking(() -> assertEquals(0, facade.findClasses("Foo", scope).length));
    PsiTestUtil.addLibrary(myFixture.getModule(), "cas", lib.getPath(), ArrayUtil.EMPTY_STRING_ARRAY, new String[]{"/classesAndSources.jar!/"});

    VirtualFile vfile = lib.findChild("classesAndSources.jar");
    assertNotNull(vfile);
    vfile = JarFileSystem.getInstance().getJarRootForLocalFile(vfile);
    assertNotNull(vfile);
    vfile = vfile.findChild("LibraryClass.java");
    assertNotNull(vfile);

    ReadAction.runBlocking(() -> assertEquals(0, facade.findClasses("LibraryClass", scope).length));

    checkFileIsNotLoadedAndHasNoIndexedStub(vfile);
  }

  private void checkFileIsNotLoadedAndHasNoIndexedStub(VirtualFile vfile) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiFileImpl file = (PsiFileImpl)PsiManager.getInstance(myFixture.getProject()).findFile(vfile);
      assertNotNull(file);
      assertFalse(file.isContentsLoaded());
      assertNull(StubTreeLoader.getInstance().readFromVFile(myFixture.getProject(), vfile));
      assertFalse(StubTreeLoader.getInstance().canHaveStub(vfile));
      assertNotNull(file.getStub());
    });
  }

  @Test
  public void testLibraryClassWithoutConstructors() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile lib =
      StandardFileSystems.local().refreshAndFindFileByPath(JavaTestUtil.getJavaTestDataPath() + "/libResolve/classWithoutConstructors");

    PsiTestUtil.addLibrary(myFixture.getModule(), "lib", lib.getPath(), new String[] {"/test.jar!/"}, ArrayUtil.EMPTY_STRING_ARRAY);
    myFixture.configureByText("p/Generous.java", """
      package p;

      import com.pack.HiddenConstructor;
      import com.pack.ValueClass;
      
      class Generous {
        static void main(){
          HiddenConstructor h = new HiddenConstructor<error descr="Cannot resolve constructor 'HiddenConstructor()'">()</error>;
          ValueClass v = new ValueClass<error descr="Cannot resolve constructor 'ValueClass()'">()</error>;
        }
      }
      """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testDirectoryWithClassFilesInsideProjectContent() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String testData = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/interJarDependencies";
    myFixture.setTestDataPath(testData);
    PsiTestUtil.addLibrary(myFixture.getModule(), "lib2", testData, "lib2.jar");

    myFixture.copyDirectoryToProject("lib1", "lib1");
    PsiTestUtil.addLibrary(myFixture.getModule(), "lib1", myFixture.getTempDirFixture().getFile("").getPath(), "lib1");

    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("TestCase.java", """
      class TestCase {
          public static void main( String[] args ) {
              new B().<error descr="Cannot resolve method 'a' in 'B'">a</error>(); // should not work, because the A in lib1 has no method a
              new B().a2(); // should work, because the A with this method is in lib1
          }
      }
      """).getVirtualFile());
    myFixture.checkHighlighting();
  }

  @Test
  public void testUpdateMethodHierarchyOnClassFileChange() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/libResolve/methodHierarchy");
    myFixture.copyDirectoryToProject("", "lib");
    PsiTestUtil.addLibrary(myFixture.getModule(), "lib", myFixture.getTempDirFixture().getFile("").getPath(), "lib");

    final PsiClass message = ReadAction.computeBlocking(() -> JavaPsiFacade.getInstance(myFixture.getProject())
      .findClass("com.google.protobuf.AbstractMessageLite", GlobalSearchScope.allScope(myFixture.getProject())));
    assertNotNull(message);

    PsiMethod method = ReadAction.computeBlocking(() -> message.findMethodsByName("toByteArray", false)[0]);
    assertNotNull(method);
    ReadAction.runBlocking(() -> assertEquals(1, method.getHierarchicalMethodSignature().getSuperSignatures().size()));

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (ThrowableComputable<Void, IOException>)() -> {
      message.getInterfaces()[0].getContainingFile().getVirtualFile().delete(this);
      return null;
    });
    ReadAction.runBlocking(() -> assertTrue(method.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()));
  }

  @Test
  public void testNestedGenericSignatureFromBinary() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/libResolve/genericSignature");
    myFixture.copyDirectoryToProject("", "lib");
    PsiTestUtil.addLibrary(myFixture.getModule(), "lib", myFixture.getTempDirFixture().getFile("").getPath(), "lib");

    JavaPsiFacadeEx javaPsiFacade = JavaPsiFacadeEx.getInstanceEx(myFixture.getProject());
    PsiElementFactory factory = javaPsiFacade.getElementFactory();

    ReadAction.runBlocking(() -> {
      PsiClass parameterizedTypes =
        JavaPsiFacade.getInstance(myFixture.getProject()).findClass("pkg.ParameterizedTypes", GlobalSearchScope.allScope(myFixture.getProject()));
      assertNotNull(parameterizedTypes);

      PsiTypeParameter parameterP = parameterizedTypes.getTypeParameters()[0];
      assertEquals("P", parameterP.getName());

      PsiClass classInner = parameterizedTypes.getInnerClasses()[0];
      assertEquals("Inner", classInner.getName());

      PsiTypeParameter parameterI = classInner.getTypeParameters()[0];
      assertEquals("I", parameterI.getName());

      PsiMethod unspecificMethod = parameterizedTypes.findMethodsByName("getUnspecificInner", false)[0];
      PsiClassType unspecificReturnType = (PsiClassType)unspecificMethod.getReturnType();
      assertEquals("pkg.ParameterizedTypes<P>.Inner<java.lang.String>", unspecificReturnType.getCanonicalText());

      PsiClassType.ClassResolveResult unspecificResolveResult = unspecificReturnType.resolveGenerics();
      assertEquals(classInner, unspecificResolveResult.getElement());

      PsiClassType unspecificOuter = factory.createType(parameterizedTypes, unspecificResolveResult.getSubstitutor());
      assertEquals("pkg.ParameterizedTypes<P>", unspecificOuter.getCanonicalText());

      PsiMethod specificMethod = parameterizedTypes.findMethodsByName("getSpecificInner", false)[0];
      PsiClassType specificReturnType = (PsiClassType)specificMethod.getReturnType();
      assertEquals("pkg.ParameterizedTypes<java.lang.Number>.Inner<java.lang.String>", specificReturnType.getCanonicalText());

      PsiClassType.ClassResolveResult specificResolveResult = specificReturnType.resolveGenerics();
      assertEquals(classInner, specificResolveResult.getElement());

      PsiSubstitutor substitutor = specificResolveResult.getSubstitutor();
      Map<PsiTypeParameter, PsiType> substitutionMap = substitutor.getSubstitutionMap();
      assertTrue(substitutionMap.containsKey(parameterI));
      assertTrue(substitutionMap.containsKey(parameterP));
      assertEquals("java.lang.Number", substitutor.substitute(parameterP).getCanonicalText());

      PsiClassType specificOuter = factory.createType(parameterizedTypes, specificResolveResult.getSubstitutor());
      assertEquals("pkg.ParameterizedTypes<java.lang.Number>", specificOuter.getCanonicalText());
    });
  }

  @Test
  public void testExtendingInnerTypesOfParameterisedClassesInExternalJars() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile classesDir = myFixture.getTempDirFixture().findOrCreateDir("classes");
    PsiTestUtil.addLibrary(myFixture.getModule(), classesDir.getPath());
    final VirtualFile libSrc = myFixture.addFileToProject("Child.java", """
      package p;
      class Parent<T> {

          protected class InnerBase {
              public final T t;

              public InnerBase(T t) {
                  this.t = t;
              }
          }
      }

      public abstract class Child<T> extends Parent<T> {
          public abstract void evaluate(InnerImpl market);

          public final class InnerImpl extends InnerBase {
              public InnerImpl(T t) {
                  super(t);
              }
          }
      }
      """).getVirtualFile();
    IdeaTestUtil.compileFile(VfsUtilCore.virtualToIoFile(libSrc), VfsUtilCore.virtualToIoFile(classesDir));
    VfsUtil.markDirtyAndRefresh(false, true, true, classesDir);
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject()); // wait for indexes after VFS refresh

    WriteAction.runAndWait(() -> libSrc.delete(null));

    ReadAction.runBlocking(() -> assertNotNull(myFixture.findClass("p.Parent")));
    ReadAction.runBlocking(() -> assertNotNull(myFixture.findClass("p.Child")));

    myFixture.configureByText("p/a.java", """
      package p;

      class MyChild extends Child<String> {
          @Override
          public void evaluate(InnerImpl impl) {
              String s = impl.<caret>t;
          }
      }
      """);
    myFixture.checkHighlighting();
    ReadAction.runBlocking(() -> assertTrue(((PsiReferenceExpression)myFixture.getReferenceAtCaretPosition()).getType().equalsToText(String.class.getName())));
  }

  private static <Value extends Sdk> Value setProjectSdk(ProjectRootManager propOwner, Value sdk) {
    propOwner.setProjectSdk(sdk);
    return sdk;
  }
}
