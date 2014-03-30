/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.migration;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.JavaTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class MigrationTest extends MultiFileTestCase {
  public void testUnexistingClassInUnexistingPackage() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("qqq.aaa.Yahoo", "java.lang.String", MigrationMapEntry.CLASS, false)
    })));
  }

  public void testToNonExistentClass() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("qqq.aaa.Yahoo", "zzz.bbb.QQQ", MigrationMapEntry.CLASS, false)
    })));
  }

  public void testPackage() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("qqq", "java.lang", MigrationMapEntry.PACKAGE, true)
    })));
  }

  public void testPackageToNonExistentPackage() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("qqq", "zzz.bbb", MigrationMapEntry.PACKAGE, true)
    })));
  }

  public void testTwoClasses() throws Exception {
    doTest(createAction(new MigrationMap(new MigrationMapEntry[]{
      new MigrationMapEntry("A", "A1", MigrationMapEntry.CLASS, true),
      new MigrationMapEntry("B", "B1", MigrationMapEntry.CLASS, true)
    })));
  }

  private MultiFileTestCase.PerformAction createAction(final MigrationMap migrationMap) {
    return new MultiFileTestCase.PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        new MigrationProcessor(myProject, migrationMap).run();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    };
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/migration/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
