// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.collections;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see ReplaceWithMutableCollectionIntention
 */
@TestApplication
public class ReplaceWithMutableCollectionIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_15);
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "collections/to_mutable_collection");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    myFixture.addClass(
      """
        package com.google.common.collect;

        import java.io.Serializable;
        import java.util.AbstractCollection;

        public abstract class ImmutableCollection<E> extends AbstractCollection<E> implements Serializable {
         \s
        }""");
    myFixture.addClass(
      """
        package com.google.common.collect;

        import java.util.Set;

        public abstract class ImmutableSet<E> extends ImmutableCollection<E> implements Set<E> {
          public static <E> ImmutableSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E... others) {
            return null;
          }
        }""");
    myFixture.addClass(
      """
        package com.google.common.collect;
        import java.util.List;

        public abstract class ImmutableList<E> extends ImmutableCollection<E> implements List<E> {
          public static <E> ImmutableList<E> of(E e1, E e2) {
            return null;
          }
        }""");
  }

  @Test
  public void testMapOf() { doTest("new HashMap<>()"); }
  @Test
  public void testMapOfEntries() { doTest("new HashMap<>()"); }
  @Test
  public void testMapOfEntriesNested() { doTest("new HashMap<>()"); }
  @Test
  public void testListOf() { doTest("new ArrayList<>()"); }
  @Test
  public void testLambdaExpr() { doTest("new HashMap<>()"); }
  @Test
  public void testSingletonMap() { doTest("new HashMap<>()"); }
  @Test
  public void testSingletonList() { doTest("new ArrayList<>()"); }
  @Test
  public void testEmpty() { doTest("new ArrayList<>()"); }
  @Test
  public void testDeclaration() { doTest("new HashMap<>()"); }
  @Test
  public void testAssignment() { doTest("new HashMap<>()"); }
  @Test
  public void testSwitchExpression() { doTest("new ArrayList<>()"); }
  @Test
  public void testAssignmentInSwitchExpression() { doTest("new ArrayList<>()"); }
  @Test
  public void testMapOfEntriesTernary() { ipp.get().assertIntentionNotAvailable(ReplaceWithMutableCollectionIntention.class); }
  @Test
  public void testMapOfEntriesArrayAccess() { ipp.get().assertIntentionNotAvailable(ReplaceWithMutableCollectionIntention.class); }
  @Test
  public void testVarArgCall() { doTest("new HashMap<>()"); }
  @Test
  public void testFieldAssignment() { doTest("new HashSet<>()"); }
  @Test
  public void testGenericMethod() { ipp.get().assertIntentionNotAvailable(ReplaceWithMutableCollectionIntention.class); }
  @Test
  public void testGenericMethodWithKnownType() { doTest("new HashSet<>()"); }
  @Test
  public void testNonTrivialQualifier() { doTest("new HashMap<>()"); }
  @Test
  public void testVolatileField() { doTest("new HashMap<>()"); }
  @Test
  public void testImmutableListAssignment() { ipp.get().assertIntentionNotAvailable(ReplaceWithMutableCollectionIntention.class); }
  @Test
  public void testImmutableSetVarArgArray() { doTest("new HashSet<>()"); }
  @Test
  public void testImmutableSetVarArgTernary() { doTest("new HashSet<>()"); }
  @Test
  public void testInsideTernary() { doTest("new ArrayList<>()"); }
  @Test
  public void testShorten() { doTest("new ArrayList<>()"); }
  private void doTest(@NotNull String expression) {
    ipp.get().doTest(IntentionPowerPackBundle.message("replace.with.mutable.collection.intention.intention.name", expression));
  }
}