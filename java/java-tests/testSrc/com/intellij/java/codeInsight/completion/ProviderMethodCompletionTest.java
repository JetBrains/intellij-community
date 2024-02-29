// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.Optional;

@NeedsIndex.SmartMode(reason = "Provider method completion is not supported in the dumb mode")
public class ProviderMethodCompletionTest extends LightFixtureCompletionTestCase {
  private MultiModuleProjectDescriptor myDescriptor;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return myDescriptor == null
           ? myDescriptor =
             new MultiModuleProjectDescriptor(Paths.get(getTestDataPath()), "providers", null)
           : myDescriptor;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/providerMethod";
  }

  public void testNonModuleClass() {
    doTest("src/org/jetbrains/providers/SimpleClass.java", "private",
           "protected");
  }

  public void testSimpleProvider() {
    doTest("src/org/jetbrains/providers/MyProviderImpl.java", "provider",
           "private",
           "protected");
  }

  public void testWithRecord() {
    doTest("src/org/jetbrains/providers/MyRecord.java", "provider",
           "private",
           "protected");
  }

  public void testWithProvider() {
    doTest("src/org/jetbrains/providers/WithProvider.java", "private",
           "protected");
  }

  public void testSubClass() {
    doTest("src/org/jetbrains/providers/MySuperClass.java", "provider",
           "private",
           "protected");
  }

  public void testWrongPlace() {
    doTest("src/org/jetbrains/providers/WrongPlace.java", "process", "program");
  }

  public void testWrongPlace2() {
    doTest("src/org/jetbrains/providers/WrongPlace2.java");
  }

  private void doTest(String path, String... names) {
    VirtualFile file = getModule().getModuleFile().getParent().findFileByRelativePath(path);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.completeBasic();

    if (names.length != 0) {
      assertStringItems(names);

      Optional<LookupElement> item = getLookup().getItems().stream().filter(le -> "provider".equals(le.getLookupString())).findFirst();
      if (item.isPresent()) {
        myFixture.getLookup().setCurrentItem(item.get());
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
      }
    }
    else {
      LookupImpl lookup = getLookup();
      if (lookup != null) {
        assertStringItems(names);
      }
    }
    checkResultByFile("after/" + path);
  }
}
