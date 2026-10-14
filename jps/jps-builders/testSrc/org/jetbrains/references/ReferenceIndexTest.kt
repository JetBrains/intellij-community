// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.references

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import org.junit.jupiter.api.Test

class ReferenceIndexTest : ReferenceIndexTestBase() {
  override fun getTestDataRootPath(): String {
    return FileUtil.toCanonicalPath(PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/referencesIndex").absolutePath, '/')
  }

  @Test
  fun testIncrementalIndexUpdate() {
    assertIndexOnRebuild("Bar.java", "Foo.java", "FooImpl.java")
    changeFileContent("Bar.java", "Bar_1.java")
    buildAllModules()
    assertIndexEquals("afterMakeIndex.txt")
    changeFileContent("FooImpl.java", "FooImpl_2.java")
    buildAllModules()
    assertIndexEquals("afterSecondMakeIndex.txt")
  }

  @Test
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

  @Test
  fun testIncrementalIndexUpdate3() {
    assertIndexOnRebuild("Foo.java")
    changeFileContent("Foo.java", "Foo_2.java")
    addFile("Bar.java")
    buildAllModules()
    assertIndexEquals("after1MakeIndex.txt")
  }

  @Test
  fun testFileCaseOnlyRename() {
    assertIndexOnRebuild("Bar.java")
    renameFile("Bar.java", "bar.java")
    buildAllModules()
    assertIndexEquals("afterRename.txt")
  }

  @Test
  fun testUnusedImports() {
    assertIndexOnRebuild("Bar.java")
  }

  @Test
  fun testUnusedImports2() {
    assertIndexOnRebuild("Main.java",
                         "com/ru/Some.java",
                         "com/ru/Some2.java",
                         "com/ru/Some3.java")
  }

  @Test
  fun testDeadCode() {
    assertIndexOnRebuild("Bar.java")
  }

  @Test
  fun testPackageInfo() {
    assertIndexOnRebuild("myPackage/package-info.java")
  }

  @Test
  fun testPackageInfo2() {
    assertIndexOnRebuild("myPackage/package-info.java")
  }

  @Test
  fun testArrayRefs() {
    assertIndexOnRebuild("Array.java", "Foo.java", "Bar.java")
  }

  @Test
  fun testTypeParameterRefs() {
    assertIndexOnRebuild("TypeParam.java")
  }

  @Test
  fun testAnonymous() {
    assertIndexOnRebuild("Anonymous.java")
  }

  @Test
  fun testPrivateMembers() {
    assertIndexOnRebuild("PrivateMembers.java")
  }

  @Test
  fun testClassDeleted() {
    assertIndexOnRebuild("Foo.java")
    changeFileContent("Foo.java", "Foo_1.java")
    buildAllModules()
    assertIndexEquals("classDeletedIndex.txt")
  }

  @Test
  fun testFileDeleted() {
    assertIndexOnRebuild("Foo.java", "Bar.java")
    changeFileContent("Foo.java", "Foo_1.java")
    deleteFile("m/Bar.java")
    buildAllModules()
    assertIndexEquals("fileDeletedIndex.txt")
  }

  @Test
  fun testCompilationUnitContains2Decls() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testMultiFileMultiUnitCompilation()  {
    assertIndexOnRebuild("Foo.java", "Boo.java", "Bar.java")
  }

  @Test
  fun testNestedClasses() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testStaticallyImportedConstant() {
    assertIndexOnRebuild("pack/Foo.java", "pack/Bar.java")
  }

  @Test
  fun testOccurrences() {

    assertIndexOnRebuild("Foo.java", "Bar.java")
  }

  @Test
  fun testConstructors() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testAnnotation() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testUnqualifiedMethodCallResolution() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testUnqualifiedMethodCallResolution2() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testUnqualifiedMethodCallResolution3() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testUnqualifiedMethodCallResolution4() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testUnqualifiedMethodCallResolution5() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testQualifierResolution() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testSignatureDataIndex() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testClassWithModifiers() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testParameterlessExplicitConstructor() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testDefaultConstructorUsage() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testCastData() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testCastDataArrays() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testCastDataGenerics() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testNestedAnonymouses() {
    assertIndexOnRebuild("Anonymouses.java", "Classes.java")
  }

  @Test
  fun testImplicitToString() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testImplicitToStringPrimitives() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testImplicitToStringLongObject() {
    assertIndexOnRebuild("Foo.java")
  }

  @Test
  fun testImplicitToStringHierarchy() {
    assertIndexOnRebuild("Foo.java")
  }
}

