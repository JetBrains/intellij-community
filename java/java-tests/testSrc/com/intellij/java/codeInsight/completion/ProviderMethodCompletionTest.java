// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    doTest("src/org/jetbrains/providers/SimpleClass.java", Set.of("private",
                                                                  "protected"));
  }

  public void testSimpleProvider() {
    doTest("src/org/jetbrains/providers/MyProviderImpl.java", Set.of("provider",
                                                                     "private",
                                                                     "protected"));
  }

  public void testWithProvider() {
    doTest("src/org/jetbrains/providers/WithProvider.java", Set.of("private",
                                                                   "protected"));
  }

  public void testSubClass() {
    doTest("src/org/jetbrains/providers/MySuperClass.java", Set.of("provider",
                                                                   "private",
                                                                   "protected"));
  }

  public void testWrongPlace() {
    doTest("src/org/jetbrains/providers/WrongPlace.java", Set.of("process", "program"));
  }

  public void testWrongPlace2() {
    doTest("src/org/jetbrains/providers/WrongPlace2.java", Set.of());
  }

  private void doTest(String path, Set<String> names) {
    VirtualFile file = getModule().getModuleFile().getParent().findFileByRelativePath(path);
    myFixture.configureFromExistingVirtualFile(file);
    List<LookupElement> items = Arrays.asList(myFixture.completeBasic());

    if (!names.isEmpty()) {
      // contains
      List<String> currentNames = ContainerUtil.map(items, LookupElement::getLookupString);
      assertTrue(currentNames + " should contains all " + names, currentNames.containsAll(names));
      // not contains
      if (!names.contains("provider")) {
        assertTrue("lookup items shouldn't contain a 'provider' element",
                   items.stream().map(LookupElement::getLookupString).noneMatch(name -> "provider".equals(name)));
      }

      Optional<LookupElement> item = items.stream().filter(le -> "provider".equals(le.getLookupString())).findFirst();
      if (item.isPresent()) {
        myFixture.getLookup().setCurrentItem(item.get());
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
      }
    }
    else {
      assertTrue("lookup items shouldn't contain a 'provider' element",
                 items.stream().map(LookupElement::getLookupString).noneMatch(name -> "provider".equals(name)));
    }
    checkResultByFile("after/" + path);
  }
}
