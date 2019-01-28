/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.ether;

public class ImportTest extends IncrementalTestCase {
  public ImportTest() {
    super("imports");
  }

  public void testUnusedClassImport() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticWildcardImport() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticImportClassDeleted() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticImportFieldDeleted() {
    setupInitialProject();
    setupModules();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticImportFieldBecameNonstatic() {
    setupInitialProject();
    setupModules();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticImportMethodDeleted() {
    setupInitialProject();
    setupModules();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticImportMethodBecameNonstatic() {
    setupInitialProject();
    setupModules();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticImportInheritedFieldDeleted() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticImportInheritedFieldBecameNonstatic() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticImportInheritedMethodDeleted() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testUnusedStaticImportInheritedMethodBecameNonstatic() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testWildcardStaticImportFieldAdded() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testWildcardStaticImportMethodAdded() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testWildcardStaticImportFieldBecameStatic() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }

  public void testWildcardStaticImportMethodBecameStatic() {
    setupInitialProject();
    doTestBuild(1).assertFailed();
  }
}
