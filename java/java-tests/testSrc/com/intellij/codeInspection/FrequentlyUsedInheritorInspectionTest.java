package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.compiler.CompilerReferencesTestBase;
import com.intellij.compiler.inspection.ChangeSuperClassFix;
import com.intellij.compiler.inspection.FrequentlyUsedInheritorInspection;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

@SkipSlowTestLocally
public class FrequentlyUsedInheritorInspectionTest extends CompilerReferencesTestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    installCompiler();
    myFixture.enableInspections(FrequentlyUsedInheritorInspection.class);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/smartInheritance/";
  }

  public void testRelevantClassShowed() {
    doTest(Pair.create("B", 12));
  }

  public void testAnonymousClasses() {
    doTest(Pair.create("B", 7));
  }

  public void testAnonymousClassesInStats() {
    doTest(Pair.create("A", 6));
  }

  public void testAbstractClass() {
    doTest(Pair.create("B", 7));
  }

  public void  testNoCompletionForAbstractClasses() {
    assertEmptyResult();
  }

  public void testNoMoreThanMaxCountIntentions() {
    doTest(FrequentlyUsedInheritorInspection.MAX_RESULT);
  }

  private void assertEmptyResult() {
    doTest();
  }

  private void doTest(final Pair<String, Integer>... expectedResults) {
    myFixture.configureByFile(getTestName(false) + ".java");
    rebuildProject();

    final Set<Pair<String, Integer>> actualSet = new HashSet<Pair<String, Integer>>();

    for (Pair<String, Integer> pair : expectedResults) {
      IntentionAction action = myFixture.findSingleIntention("Make extends '" + pair.getFirst());
      IntentionAction intentionAction = ((IntentionActionDelegate)action).getDelegate();
      if (intentionAction instanceof QuickFixWrapper) {
        ChangeSuperClassFix changeSuperClassFix = getQuickFixFromWrapper((QuickFixWrapper)intentionAction);
        if (changeSuperClassFix != null) {
          actualSet.add(Pair.create(changeSuperClassFix.getNewSuperClass().getQualifiedName(), changeSuperClassFix.getInheritorCount()));
        }
      }
    }
    final Set<Pair<String, Integer>> expectedSet = ContainerUtil.newHashSet(expectedResults);
    assertEquals(actualSet, expectedSet);
  }

  private void doTest(final int expectedSize) {
    myFixture.configureByFile(getTestName(false) + ".java");
    rebuildProject();

    List<IntentionAction> actions = myFixture.filterAvailableIntentions("Make extends '");

    assertEquals(expectedSize, actions.size());
  }

  @Nullable
  private static ChangeSuperClassFix getQuickFixFromWrapper(final QuickFixWrapper quickFixWrapper) {
    final LocalQuickFix quickFix = quickFixWrapper.getFix();
    if (quickFix instanceof ChangeSuperClassFix) {
      return (ChangeSuperClassFix)quickFix;
    }
    return null;
  }
}
