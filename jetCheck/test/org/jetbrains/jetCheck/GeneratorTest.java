/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jetCheck;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jetbrains.jetCheck.Generator.*;

/**
 * @author peter
 */
public class GeneratorTest extends PropertyCheckerTestCase {

  public void testMod() {
    checkFalsified(integers(),
                   i -> i % 12 != 0,
                   1);
  }

  public void testListSumMod() {
    checkFalsified(nonEmptyLists(integers()),
                   l -> l.stream().mapToInt(Integer::intValue).sum() % 10 != 0,
                   301);
  }

  public void testListContainsDivisible() {
    checkGeneratesExample(nonEmptyLists(integers()),
                          l -> l.stream().anyMatch(i -> i % 10 == 0),
                          4);
  }

  public void testStringContains() {
    assertEquals("a", checkGeneratesExample(stringsOf(asciiPrintableChars()),
                                            s -> s.contains("a"),
                                            10));

    String aWithB = checkGeneratesExample(stringsOf(IntDistribution.uniform(2, 100), asciiPrintableChars()),
                                     s -> s.contains("a") && s.contains("b"),
                                     27);
    assertTrue(aWithB, "ab".equals(aWithB) || "ba".equals(aWithB));
  }

  public void testLetterStringContains() {
    checkFalsified(stringsOf(asciiLetters()),
                   s -> !s.contains("a"),
                   1);
  }

  public void testIsSorted() {
    PropertyFailure<List<Integer>> failure = checkFalsified(nonEmptyLists(integers()),
                                                            l -> l.stream().sorted().collect(Collectors.toList()).equals(l),
                                                            36);
    List<Integer> value = failure.getMinimalCounterexample().getExampleValue();
    assertEquals(2, value.size());
    assertTrue(value.toString(), value.stream().allMatch(i -> Math.abs(i) < 2));
  }

  public void testSuccess() {
    PropertyChecker.forAll(listsOf(integers(-1, 1)), l -> l.stream().allMatch(i -> Math.abs(i) <= 1));
  }

  public void testSortedDoublesNonDescending() {
    PropertyFailure<List<Double>> failure = checkFalsified(listsOf(doubles()),
                                                           l -> isSorted(l.stream().sorted().collect(Collectors.toList())),
                                                           23);
    assertEquals(2, failure.getMinimalCounterexample().getExampleValue().size());
  }

  private static boolean isSorted(List<Double> list) {
    for (int i = 0; i < list.size() - 1; i++) {
      double d1 = list.get(i);
      double d2 = list.get(i + 1);
      if (!(d1 <= d2)) return false;
    }
    return true;
  }

  public void testSuchThat() {
    PropertyChecker.forAll(integers().suchThat(i -> i < 0), i -> i < 0);
  }

  public void testNestedSometimesVeryRareSuchThat() {
    STABLE.forAll(frequency(50, constant(0), 1, integers(1, 1000)).suchThat(i -> i > 0), i -> i > 0);
  }

  public void testStringOfStringChecksAllChars() {
    checkFalsified(stringsOf("abc "),
                   s -> !s.contains(" "),
                   0);
  }

  public void testListNotLongerThanMaxDefaultSize() {
    PropertyChecker.customized().withIterationCount(100_000).forAll(listsOf(integers()), l -> l.size() <= PropertyChecker.DEFAULT_MAX_SIZE_HINT);
  }

  public void testNonEmptyList() {
    PropertyChecker.forAll(nonEmptyLists(integers()), l -> !l.isEmpty());
  }

  public void testNoDuplicateData() {
    Set<List<Integer>> visited = new HashSet<>();
    PropertyChecker.forAll(listsOf(integers()), l -> visited.add(l));
  }

  public void testOneOf() {
    List<Integer> values = new ArrayList<>();
    PropertyChecker.forAll(anyOf(integers(0, 1), integers(10, 1100)), i -> values.add(i));
    assertTrue(values.stream().anyMatch(i -> i < 2));
    assertTrue(values.stream().anyMatch(i -> i > 5));
  }

  public void testAsciiIdentifier() {
    PropertyChecker.forAll(asciiIdentifiers(), 
                           s -> Character.isJavaIdentifierStart(s.charAt(0)) && s.chars().allMatch(Character::isJavaIdentifierPart));
    checkGeneratesExample(asciiIdentifiers(),
                          s -> s.contains("_"),
                          10);
  }

  public void testBoolean() {
    List<Boolean> list = checkGeneratesExample(listsOf(booleans()),
                                               l -> l.contains(true) && l.contains(false),
                                               4);
    assertEquals(2, list.size());
  }

  @SuppressWarnings("deprecation")
  public void testRecheckWithGivenSeeds() {
    Generator<List<Integer>> gen = nonEmptyLists(integers(0, 100));
    Predicate<List<Integer>> property = l -> !l.contains(42);

    PropertyFailure<?> failure = checkFails(PropertyChecker.customized().withSeed(1), gen, property).getFailure();
    assertTrue(failure.getIterationNumber() > 1);

    PropertyFalsified e;

    e = checkFails(PropertyChecker.customized().recheckingIteration(failure.getIterationSeed(), failure.getSizeHint()), gen, property);
    assertEquals(1, e.getFailure().getIterationNumber());

    e = checkFails(PropertyChecker.customized().withSeed(failure.getGlobalSeed()), gen, property);
    assertEquals(failure.getIterationNumber(), e.getFailure().getIterationNumber());
  }

  public void testSameFrequency() {
    checkFalsified(listsOf(frequency(1, constant(1), 1, constant(2))),
                   l -> !l.contains(1) || !l.contains(2),
                   3);

    checkFalsified(listsOf(frequency(1, constant(1), 1, constant(2)).with(1, constant(3))),
                   l -> !l.contains(1) || !l.contains(2) || !l.contains(3),
                   7);
  }

  public void testReplay() {
    List<List> log = new ArrayList<>();
    PropertyFailure<List<Integer>> failure = checkFalsified(listsOf(integers(0, 100)), l -> {
      log.add(l);
      return !l.contains(42);
    }, 9);
    List<Integer> goldMin = Collections.singletonList(42);

    PropertyFailure.CounterExample<List<Integer>> first = failure.getFirstCounterExample();
    PropertyFailure.CounterExample<List<Integer>> min = failure.getMinimalCounterexample();
    assertEquals(goldMin, min.getExampleValue());
    assertTrue(log.contains(first.getExampleValue()));
    assertTrue(log.contains(min.getExampleValue()));
    
    log.clear();
    PropertyFailure.CounterExample<List<Integer>> first2 = first.replay();
    assertEquals(first.getExampleValue(), first2.getExampleValue());
    assertEquals(log, Collections.singletonList(first2.getExampleValue()));
    
    log.clear();
    PropertyFailure.CounterExample<List<Integer>> min2 = min.replay();
    assertEquals(goldMin, min2.getExampleValue());
    assertEquals(log, Collections.singletonList(goldMin));
  }

  public void testShrinkToRangeStart() {
    PropertyFailure<String> failure = checkFalsified(stringsOf(asciiUppercaseChars()), s -> s.length() < 5, 11);
    assertEquals("AAAAA", failure.getMinimalCounterexample().getExampleValue());
  }

}
