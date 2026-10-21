/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.ide.fileStructure;

import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Konstantin Bulenkov
 */
@TestApplication
public class JavaFileStructureHierarchyTest extends JavaFileStructureTestCase {
  @Override
  protected String getTestDataFolderName() {
    return "hierarchy";
  }

  @BeforeEach
  void setUp() {
    myFixture.copyDirectoryToProject("", "");
  }

  @Override
  protected void configurePopup() {
    super.configurePopup();
    setShowParents(true);
  }

  @Test public void testSimple() {checkTree();}
  @Test public void testAnonymousAsConstantInInterface() {checkTree("getA");}
  @Test public void testAnonymousHashCode() {checkTree("hash");}
}
