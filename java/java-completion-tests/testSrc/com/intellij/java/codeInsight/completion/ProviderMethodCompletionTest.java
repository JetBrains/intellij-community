// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.testFramework.fixtures.MultiModuleProject;
import com.intellij.java.testFramework.fixtures.MultiModuleProjectDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.javaModulesCodeInsightFixture;
import static com.intellij.java.testFramework.fixtures.JavaModulesFixturesKt.multiModuleProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NeedsIndex.SmartMode(reason = "Provider method completion is not supported in the dumb mode")
@TestApplication
@IndexingModeTemplate
public class ProviderMethodCompletionTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/providerMethod";

  private final TestFixture<MultiModuleProject> projectFixture =
    multiModuleProjectFixture(() -> new MultiModuleProjectDescriptor(Paths.get(TEST_DATA_PATH), "providers", null));
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModulesCodeInsightFixture(projectFixture, TEST_DATA_PATH);
  private JavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable());
  }

  @Test
  public void testNonModuleClass() {
    doTest("src/org/jetbrains/providers/SimpleClass.java", Set.of("private",
                                                                  "protected"));
  }

  @Test
  public void testSimpleProvider() {
    doTest("src/org/jetbrains/providers/MyProviderImpl.java", Set.of("provider",
                                                                     "private",
                                                                     "protected"));
  }

  @Test
  public void testWithProvider() {
    doTest("src/org/jetbrains/providers/WithProvider.java", Set.of("private",
                                                                   "protected"));
  }

  @Test
  public void testSubClass() {
    doTest("src/org/jetbrains/providers/MySuperClass.java", Set.of("provider",
                                                                   "private",
                                                                   "protected"));
  }

  @Test
  public void testWrongPlace() {
    doTest("src/org/jetbrains/providers/WrongPlace.java", Set.of("process", "program"));
  }

  @Test
  public void testWrongPlace2() {
    doTest("src/org/jetbrains/providers/WrongPlace2.java", Set.of());
  }

  private void doTest(String path, Set<String> names) {
    VirtualFile file = projectFixture.get().getModule().getModuleFile().getParent().findFileByRelativePath(path);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureFromExistingVirtualFile(file);
      List<LookupElement> items = Arrays.asList(myFixture.completeBasic());

      if (!names.isEmpty()) {
        // contains
        List<String> currentNames = ContainerUtil.map(items, LookupElement::getLookupString);
        assertTrue(currentNames.containsAll(names), currentNames + " should contains all " + names);
        // not contains
        if (!names.contains("provider")) {
          assertTrue(items.stream().map(LookupElement::getLookupString).noneMatch(name -> "provider".equals(name)),
                     "lookup items shouldn't contain a 'provider' element");
        }

        Optional<LookupElement> item = items.stream().filter(le -> "provider".equals(le.getLookupString())).findFirst();
        if (item.isPresent()) {
          myFixture.getLookup().setCurrentItem(item.get());
          myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        }
      }
      else {
        assertTrue(items.stream().map(LookupElement::getLookupString).noneMatch(name -> "provider".equals(name)),
                   "lookup items shouldn't contain a 'provider' element");
      }
      myFixture.checkResultByFile("after/" + path);
    });
  }
}
