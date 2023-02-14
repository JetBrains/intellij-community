// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.resolve

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.psi.*
import com.intellij.psi.impl.JavaPsiFacadeEx
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.stubs.StubTreeLoader
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import groovy.transform.CompileStatic

class ResolveInLibrariesTest extends JavaCodeInsightFixtureTestCase {
  void "test inheritance transitivity"() {
    def protobufJar = IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("protobuf")
    VirtualFile jarCopy = WriteAction.compute {
      JarFileSystem.instance.getLocalVirtualFileFor(protobufJar).copy(this, myFixture.getTempDirFixture().findOrCreateDir("lib"), "protoJar.jar")
    }

    PsiTestUtil.addProjectLibrary(module, 'proto1', [protobufJar], [])
    PsiTestUtil.addProjectLibrary(module, 'proto2', [JarFileSystem.instance.getJarRootForLocalFile(jarCopy)], [])

    def scope = GlobalSearchScope.allScope(project)

    def bottoms = JavaPsiFacade.getInstance(project).findClasses('com.google.protobuf.AbstractMessage', scope)
    assert bottoms.size() == 2

    def middles = JavaPsiFacade.getInstance(project).findClasses('com.google.protobuf.AbstractMessageLite', scope)
    assert middles.size() == 2

    def interfaces = JavaPsiFacade.getInstance(project).findClasses('com.google.protobuf.MessageLite', scope)
    assert interfaces.size() == 2

    for (i in 0..1) {
      assert ClassInheritorsSearch.search(interfaces[i]).findAll().containsAll([middles[i], bottoms[i]])
      interfaces[i].methods.each {
        assert OverridingMethodsSearch.search(it).findAll()
      }

      assert middles[i].isInheritor(interfaces[i], true)
      assert bottoms[i].isInheritor(interfaces[i], true)
      assert bottoms[i].isInheritor(middles[i], true)
    }
  }

  @CompileStatic
  void "test missed library dependency"() {
    def projectRootManager = ProjectRootManager.getInstance(myFixture.project)
    def oldSdk = projectRootManager.projectSdk
    try {
      WriteAction.run { projectRootManager.projectSdk = IdeaTestUtil.getMockJdk17() }

      // add jar with org.codehaus.groovy.ant.Groovydoc
      ModuleRootModificationUtil.updateModel(module) { model ->
        MavenDependencyUtil.addFromMaven(model, "org.codehaus.groovy:groovy-ant:2.4.17", false)
      }

      // add jar with org.apache.tools.ant.Task which is the superclass of org.codehaus.groovy.ant.Groovydoc
      def ant = IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("Ant")
      PsiTestUtil.addProjectLibrary(module, 'ant', ant)

      myFixture.configureByText("Foo.java", """
  class Foo { 
    {new org.codehaus.groovy.ant.Groovydoc().set<caret>Project(new org.apache.tools.ant.Project());}
  }""")

      assertNotNull(myFixture.getElementAtCaret())
    }
    finally {
      WriteAction.run { projectRootManager.projectSdk = oldSdk }
    }
  }

  void "test accept that with different library versions inheritance relation may be intransitive"() {
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/libResolve/inheritance")

    //Foo, Middle implements Foo, Other extends Middle
    PsiTestUtil.addLibrary(module, 'full', lib.path, ["/fullLibrary.jar!/"] as String[], [] as String[])

    //Middle, Bottom extends Middle
    PsiTestUtil.addLibrary(module, 'partial', lib.path, ["/middleBottom.jar!/"] as String[], [] as String[])

    def scope = GlobalSearchScope.allScope(project)

    //noinspection SpellCheckingInspection
    def i0 = JavaPsiFacade.getInstance(project).findClass('Intf', scope)
    def other0 = JavaPsiFacade.getInstance(project).findClass('Other', scope)
    def b1 = JavaPsiFacade.getInstance(project).findClass('Bottom', scope)

    def middles = JavaPsiFacade.getInstance(project).findClasses('Middle', scope)
    assert middles.size() == 2
    def m0 = middles[0]
    def m1 = middles[1]

    for (deep in [false, true]) {
      assert m0.isInheritor(i0, deep)
      assert other0.isInheritor(m0, deep)

      assert !b1.isInheritor(i0, deep)

      assert b1.isInheritor(m0, deep)
      assert b1.isInheritor(m1, deep)

      assert !m1.isInheritor(i0, deep)
    }

    assert other0.isInheritor(i0, true)
    assert !other0.isInheritor(i0, false)

    assert ClassInheritorsSearch.search(i0).findAll() as Set == [m0, b1, other0] as Set
    assert ClassInheritorsSearch.search(m0).findAll() as Set == [b1, other0] as Set

    assert fooInheritors(i0) == [fooMethod(m0), fooMethod(other0)] as Set
    assert fooInheritors(m0) == [fooMethod(other0), fooMethod(b1)] as Set
    assert fooInheritors(m1) == [fooMethod(other0), fooMethod(b1)] as Set
  }

  private static PsiMethod fooMethod(PsiClass c) { c.findMethodsByName('foo', false)[0] }
  private static Set<PsiMethod> fooInheritors(PsiClass c) { OverridingMethodsSearch.search(fooMethod(c)).findAll() as Set }

  void "test do not parse not stubbed sources in class jars"() {
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/libResolve/classesAndSources")
    PsiTestUtil.addLibrary(module, 'cas', lib.path, ["/classesAndSources.jar!/"] as String[], ["/classesAndSources.jar!/"] as String[])

    def facade = JavaPsiFacade.getInstance(project)
    def scope = GlobalSearchScope.allScope(project)

    assert facade.findClasses('LibraryClass', scope).size() == 1

    def pkg = facade.findPackage("")
    assert pkg.classes.size() == 1

    Collection<VirtualFile> pkgDirs = pkg.directories.collect { it.virtualFile }
    Collection<VirtualFile> pkgChildren = pkgDirs.collect { it.children as List }.flatten()
    VirtualFile javaSrc = pkgChildren.find { it.name == 'LibraryClass.java' }
    checkFileIsNotLoadedAndHasNoIndexedStub(javaSrc)

    assert pkg.containsClassNamed('LibraryClass')
    checkFileIsNotLoadedAndHasNoIndexedStub(javaSrc)
    assert !((PsiFileImpl)psiManager.findFile(javaSrc)).treeElement
  }

  @Override
  protected boolean toAddSourceRoot() {
    return name != "test do not build stubs in source jars"
  }

  void "test do not build stubs in source jars"() {
    def facade = JavaPsiFacade.getInstance(project)
    def scope = GlobalSearchScope.allScope(project)

    String testDataPathForTest = PathManagerEx.getTestDataPath() + "/libResolve/classesAndSources"
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(testDataPathForTest)
    def localFile = myFixture.copyFileToProject(testDataPathForTest + File.separator + "Foo.java", 'Foo.java')
    assert localFile != null

    checkFileIsNotLoadedAndHasNoIndexedStub(localFile)
    assert facade.findClasses('Foo', scope).size() == 0
    PsiTestUtil.addLibrary(module, 'cas', lib.path, [] as String[], ["/classesAndSources.jar!/"] as String[])

    def vfile = lib.findChild("classesAndSources.jar")
    assert vfile != null
    vfile = JarFileSystem.getInstance().getJarRootForLocalFile(vfile)
    assert vfile != null
    vfile = vfile.findChild('LibraryClass.java')
    assert vfile != null

    assert facade.findClasses('LibraryClass', scope).size() == 0

    checkFileIsNotLoadedAndHasNoIndexedStub(vfile)
  }

  private void checkFileIsNotLoadedAndHasNoIndexedStub(VirtualFile vfile) {
    PsiFileImpl file = psiManager.findFile(vfile) as PsiFileImpl
    assert file != null
    assert !file.contentsLoaded
    assert !StubTreeLoader.instance.readFromVFile(project, vfile)
    assert !StubTreeLoader.instance.canHaveStub(vfile)
    assert file.stub // from text
  }

  void "test directory with class files inside project content"() {
    def testData = PathManagerEx.getTestDataPath() + "/codeInsight/interJarDependencies"
    myFixture.setTestDataPath(testData)
    PsiTestUtil.addLibrary(module, "lib2", testData, "lib2.jar")

    myFixture.copyDirectoryToProject("lib1", "lib1")
    PsiTestUtil.addLibrary(module, "lib1", myFixture.tempDirFixture.getFile("").path, "lib1")

    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("TestCase.java", """
class TestCase {
    public static void main( String[] args ) {
        new B().<error descr="Cannot resolve method 'a' in 'B'">a</error>(); // should not work, because the A in lib1 has no method a
        new B().a2(); // should work, because the A with this method is in lib1
    }
}
""").virtualFile)
    myFixture.checkHighlighting()
  }

  void "test update method hierarchy on class file change"() {
    myFixture.testDataPath = PathManagerEx.getTestDataPath() + "/libResolve/methodHierarchy"
    myFixture.copyDirectoryToProject("", "lib")
    PsiTestUtil.addLibrary(module, "lib", myFixture.tempDirFixture.getFile("").path, "lib")

    def message = JavaPsiFacade.getInstance(project).findClass('com.google.protobuf.AbstractMessageLite', GlobalSearchScope.allScope(project))
    assert message

    def method = message.findMethodsByName("toByteArray", false)[0]
    assert method
    assert method.hierarchicalMethodSignature.superSignatures.size() == 1

    WriteCommandAction.runWriteCommandAction(project) {
      message.interfaces[0].containingFile.virtualFile.delete(this)
    }
    assert method.hierarchicalMethodSignature.superSignatures.size() == 0
  }

  void "test nested generic signature from binary"() {
    myFixture.testDataPath = PathManagerEx.getTestDataPath() + "/libResolve/genericSignature"
    myFixture.copyDirectoryToProject("", "lib")
    PsiTestUtil.addLibrary(module, "lib", myFixture.tempDirFixture.getFile("").path, "lib")

    def javaPsiFacade = JavaPsiFacadeEx.getInstanceEx(project)
    def factory = javaPsiFacade.elementFactory

    def parameterizedTypes = JavaPsiFacade.getInstance(project).findClass('pkg.ParameterizedTypes', GlobalSearchScope.allScope(project))
    assert parameterizedTypes

    def parameterP = parameterizedTypes.typeParameters[0]
    assert parameterP.name == 'P'

    def classInner = parameterizedTypes.innerClasses[0]
    assert classInner.name == 'Inner'

    def parameterI = classInner.typeParameters[0]
    assert parameterI.name == 'I'

    def unspecificMethod = parameterizedTypes.findMethodsByName("getUnspecificInner", false)[0]
    def unspecificReturnType = unspecificMethod.returnType as PsiClassType
    assert unspecificReturnType.canonicalText == 'pkg.ParameterizedTypes<P>.Inner<java.lang.String>'

    def unspecificResolveResult = unspecificReturnType.resolveGenerics()
    assert unspecificResolveResult.element == classInner

    def unspecificOuter = factory.createType(parameterizedTypes, unspecificResolveResult.substitutor)
    assert unspecificOuter.canonicalText == 'pkg.ParameterizedTypes<P>'

    def specificMethod = parameterizedTypes.findMethodsByName("getSpecificInner", false)[0]
    def specificReturnType = specificMethod.returnType as PsiClassType
    assert specificReturnType.canonicalText == 'pkg.ParameterizedTypes<java.lang.Number>.Inner<java.lang.String>'

    def specificResolveResult = specificReturnType.resolveGenerics()
    assert specificResolveResult.element == classInner

    def substitutor = specificResolveResult.substitutor
    def substitutionMap = substitutor.substitutionMap
    assert substitutionMap.containsKey(parameterI)
    assert substitutionMap.containsKey(parameterP)
    assert substitutor.substitute(parameterP).canonicalText == 'java.lang.Number'

    def specificOuter = factory.createType(parameterizedTypes, specificResolveResult.substitutor)
    assert specificOuter.canonicalText == 'pkg.ParameterizedTypes<java.lang.Number>'
  }

  void "test extending inner types of parameterised classes in external jars"() {
    def classesDir = myFixture.tempDirFixture.findOrCreateDir("classes")
    PsiTestUtil.addLibrary(module, classesDir.path)
    def libSrc = myFixture.addFileToProject("Child.java", """package p;
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
""").virtualFile
    IdeaTestUtil.compileFile(VfsUtil.virtualToIoFile(libSrc), VfsUtil.virtualToIoFile(classesDir))
    VfsUtil.markDirtyAndRefresh(false, true, true, classesDir)

    WriteAction.run { libSrc.delete() }

    assert myFixture.findClass("p.Parent")
    assert myFixture.findClass("p.Child")

    myFixture.configureByText "p/a.java", """
package p;

class MyChild extends Child<String> {
    @Override
    public void evaluate(InnerImpl impl) {
        String s = impl.<caret>t;
    }
}
"""
    myFixture.checkHighlighting()
    assert (myFixture.getReferenceAtCaretPosition() as PsiReferenceExpression).type.equalsToText(String.name)
  }
}
