package com.siyeh.igtest.migration.comparator_min_max_can_be_used;

import java.util.Comparator;

class ComparatorMinMaxCanBeUsed {
  void testGreaterThan(Comparator<String> comp, String a, String b) {
    String r1 = comp.max(b, a);
    String r2 = comp.min(b, a);
  }

  void testLessThan(Comparator<String> comp, String a, String b) {
    String r1 = comp.min(b, a);
    String r2 = comp.max(b, a);
  }

  void testGreaterThanOrEqual(Comparator<String> comp, String a, String b) {
    String r1 = comp.max(a, b);
    String r2 = comp.min(a, b);
  }

  void testLessThanOrEqual(Comparator<String> comp, String a, String b) {
    String r1 = comp.min(a, b);
    String r2 = comp.max(a, b);
  }

  void testReversedComparison(Comparator<String> comp, String a, String b) {
    // 0 < compare(a, b) is equivalent to compare(a, b) > 0
    String r1 = comp.max(b, a);
    // 0 >= compare(a, b) is equivalent to compare(a, b) <= 0
    String r2 = comp.max(a, b);
  }

  void testParenthesized(Comparator<String> comp, String a, String b) {
    String r1 = comp.max(b, a);
    String r2 = comp.min(b, a);
  }

  // If-statement patterns
  String testIfReturnGreaterThan(Comparator<String> comp, String a, String b) {
      return comp.max(b, a);
  }

  String testIfReturnLessThan(Comparator<String> comp, String a, String b) {
      return comp.min(b, a);
  }

  String testIfReturnReversedBranches(Comparator<String> comp, String a, String b) {
      return comp.min(b, a);
  }

  String testIfReturnWithBlocks(Comparator<String> comp, String a, String b) {
      return comp.max(b, a);
  }

  String testIfImplicitReturn(Comparator<String> comp, String a, String b) {
      return comp.max(b, a);
  }

  void testIfAssignment(Comparator<String> comp, String a, String b) {
    String r = comp.max(b, a);
      System.out.println(r);
  }

  void testIfOverwrittenDeclaration(Comparator<String> comp, String a, String b) {
    String r = comp.max(b, a);
      System.out.println(r);
  }

  void testIfGreaterOrEqual(Comparator<String> comp, String a, String b) {
    String r = comp.max(a, b);
      System.out.println(r);
  }

  void testIfReversedComparison(Comparator<String> comp, String a, String b) {
    String r = comp.max(b, a);
      System.out.println(r);
  }

  void testIfNoWarning(Comparator<String> comp, String a, String b) {
    // Non-matching branches
    String r;
    if (comp.compare(a, b) > 0) r = a; else r = "default";
    // Comparison to non-zero
    if (comp.compare(a, b) > 1) r = a; else r = b;
    // Equality comparison
    if (comp.compare(a, b) == 0) r = a; else r = b;
    System.out.println(r);
  }
  
  void testWithPureCall(Comparator<String> comp, String a, String b) {
    // Side effects in compare arguments
    String r1 = comp.max(b.trim(), a.trim());
  }

  void testNoWarning(Comparator<String> comp, String a, String b) {
    // Side effects in compare arguments
    String r1 = comp.compare(process(a), process(b)) > 0 ? process(a) : process(b);
    // Non-matching branches
    String r2 = comp.compare(a, b) > 0 ? a : "default";
    // Comparison to non-zero
    String r3 = comp.compare(a, b) > 1 ? a : b;
    // Equality comparison
    String r4 = comp.compare(a, b) == 0 ? a : b;
    // Not a Comparator.compare call
    int cmp = comp.compare(a, b);
    String r5 = cmp > 0 ? a : b;
    // Branches swapped with unrelated expressions
    String r6 = comp.compare(a, b) > 0 ? b : b;
  }
  
  native String process(String s);
}
