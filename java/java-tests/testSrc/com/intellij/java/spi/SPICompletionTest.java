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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class SPICompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for SPI")
  public void testQualifiedReference() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.foo; public class Interface {}");
    myFixture.addClass("package com.foo; public class Implementation extends Interface {}");
    myFixture.configureFromExistingVirtualFile(
      myFixture.addFileToProject("META-INF/services/com.foo.Interface", "com.f<caret>").getVirtualFile());
    myFixture.completeBasic();
    myFixture.checkResult("com.foo.Implementation");
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for SPI")
  public void testCompletionAfterRenaming() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.addFileToProject("META-INF/services/aaa", "<caret>").getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);
    assertThat(myFixture.completeBasic()).isNullOrEmpty();
    LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), (ThrowableComputable<Void, IOException>)() -> {
      file.rename(this, "java.lang.Runnable");
      return null;
    });

    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "java.lang.Thread");
  }

}
