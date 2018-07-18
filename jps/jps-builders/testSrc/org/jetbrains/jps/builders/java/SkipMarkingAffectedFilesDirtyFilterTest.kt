// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java

class SkipMarkingAffectedFilesDirtyFilterTest : IncrementalBuildTestCase() {
  override val testDataDirectoryName: String
    get() = "markingAffectedFilesDirtyFilter"

  fun testDoNotMarkDirty() {
    doTest(checkMappingAfterRebuild = false) {
      createFile("src/A.java", "class A { void foo() {} }")
      createFile("src/B$DO_NOT_MARK_DIRTY_SUFFIX.java", "class B$DO_NOT_MARK_DIRTY_SUFFIX { { new A().foo(); } }")
      createFile("src/C.java", "class C { { new A().foo(); } }")
      modify {
        changeFile("src/A.java", "class A { int foo() {return 0;} }")
      }
    }
  }
}