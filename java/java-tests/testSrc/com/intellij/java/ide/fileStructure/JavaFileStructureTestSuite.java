// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ide.fileStructure;

import junit.framework.TestSuite;

/**
 * @author Konstantin Bulenkov
 */
public class JavaFileStructureTestSuite {
  public static TestSuite suite() {
    return new TestSuite(
      JavaFileStructureSelectionTest.class,
      JavaFileStructureFilteringTest.class,
      JavaFileStructureHierarchyTest.class
    );
  }
}
