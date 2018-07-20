// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.references

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil

class ReferenceIndexTest : ReferenceIndexTestBase() {
  override fun getTestDataRootPath(): String {
    return FileUtil.toCanonicalPath(PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/referencesIndex").absolutePath, '/')
  }

  fun testIncrementalIndexUpdate() {
    assertIndexOnRebuild("Bar.java", "Foo.java", "FooImpl.java")
    changeFileContent("Bar.java", "Bar_1.java")
    buildAllModules()
    assertIndexEquals("afterMakeIndex.txt")
    changeFileContent("FooImpl.java", "FooImpl_2.java")
    buildAllModules()
    assertIndexEquals("afterSecondMakeIndex.txt")
  }

  fun testIncrementalIndexUpdate2() {
    assertIndexOnRebuild("Foo.java")
    changeFileContent("Foo.java", "Foo_2.java")
    buildAllModules()
    assertIndexEquals("after1MakeIndex.txt")
    deleteFile("m/Foo.java")
    addFile("Bar.java")
    buildAllModules()
    assertIndexEquals("after2MakeIndex.txt")
  }

  fun testIncrementalIndexUpdate3() {
    assertIndexOnRebuild("Foo.java")
    changeFileContent("Foo.java", "Foo_2.java")
    addFile("Bar.java")
    buildAllModules()
    assertIndexEquals("after1MakeIndex.txt")
  }

  fun testFileCaseOnlyRename() {
    assertIndexOnRebuild("Bar.java")
    renameFile("Bar.java", "bar.java")
    buildAllModules()
    assertIndexEquals("afterRename.txt")
  }

  fun testUnusedImports() {
    assertIndexOnRebuild("Bar.java")
  }

  fun testUnusedImports2() {
    assertIndexOnRebuild("Main.java",
                         "com/ru/Some.java",
                         "com/ru/Some2.java",
                         "com/ru/Some3.java")
  }

  fun testDeadCode() {
    assertIndexOnRebuild("Bar.java")
  }

  fun testPackageInfo() {
    assertIndexOnRebuild("myPackage/package-info.java")
  }

  fun testPackageInfo2() {
    assertIndexOnRebuild("myPackage/package-info.java")
  }

  fun testArrayRefs() {
    assertIndexOnRebuild("Array.java", "Foo.java", "Bar.java")
  }

  fun testTypeParameterRefs() {
    assertIndexOnRebuild("TypeParam.java")
  }

  fun testAnonymous() {
    assertIndexOnRebuild("Anonymous.java")
  }

  fun testPrivateMembers() {
    assertIndexOnRebuild("PrivateMembers.java")
  }

  fun testClassDeleted() {
    assertIndexOnRebuild("Foo.java")
    changeFileContent("Foo.java", "Foo_1.java")
    buildAllModules()
    assertIndexEquals("classDeletedIndex.txt")
  }

  fun testFileDeleted() {
    assertIndexOnRebuild("Foo.java", "Bar.java")
    changeFileContent("Foo.java", "Foo_1.java")
    deleteFile("m/Bar.java")
    buildAllModules()
    assertIndexEquals("fileDeletedIndex.txt")
  }

  fun testCompilationUnitContains2Decls() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testMultiFileMultiUnitCompilation()  {
    assertIndexOnRebuild("Foo.java", "Boo.java", "Bar.java")
  }

  fun testNestedClasses() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testStaticallyImportedConstant() {
    assertIndexOnRebuild("pack/Foo.java", "pack/Bar.java")
  }

  fun testOccurrences() {

    assertIndexOnRebuild("Foo.java", "Bar.java")
  }

  fun testConstructors() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testAnnotation() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testUnqualifiedMethodCallResolution() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testUnqualifiedMethodCallResolution2() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testUnqualifiedMethodCallResolution3() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testUnqualifiedMethodCallResolution4() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testUnqualifiedMethodCallResolution5() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testQualifierResolution() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testSignatureDataIndex() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testClassWithModifiers() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testParameterlessExplicitConstructor() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testDefaultConstructorUsage() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testCastData() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testCastDataArrays() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testCastDataGenerics() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testNestedAnonymouses() {
    assertIndexOnRebuild("Anonymouses.java", "Classes.java")
  }

  fun testImplicitToString() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testImplicitToStringPrimitives() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testImplicitToStringLongObject() {
    assertIndexOnRebuild("Foo.java")
  }

  fun testImplicitToStringHierarchy() {
    assertIndexOnRebuild("Foo.java")
  }
}

