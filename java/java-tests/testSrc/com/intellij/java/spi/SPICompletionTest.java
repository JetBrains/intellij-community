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
package com.intellij.java.spi;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.io.IOException;

/**
 * @author peter
 */
public class SPICompletionTest extends LightJavaCodeInsightFixtureTestCase {

  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for SPI")
  public void testQualifiedReference() {
    myFixture.addClass("package com.foo; public class Interface {}");
    myFixture.addClass("package com.foo; public class Implementation extends Interface {}");
    myFixture.configureFromExistingVirtualFile(
      myFixture.addFileToProject("META-INF/services/com.foo.Interface", "com.f<caret>").getVirtualFile());
    myFixture.completeBasic();
    myFixture.checkResult("com.foo.Implementation");
  }

  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for SPI")
  public void testCompletionAfterRenaming() throws IOException {
    VirtualFile file = myFixture.addFileToProject("META-INF/services/aaa", "<caret>").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);
    assertEmpty(myFixture.completeBasic());
    LookupManager.getInstance(getProject()).hideActiveLookup();

    WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<Void, IOException>)() -> {
      file.rename(this, "java.lang.Runnable");
      return null;
    });

    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "java.lang.Thread");
  }

}
