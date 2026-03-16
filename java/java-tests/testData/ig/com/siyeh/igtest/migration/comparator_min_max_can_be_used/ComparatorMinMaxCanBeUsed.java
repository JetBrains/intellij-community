package com.siyeh.igtest.migration.comparator_min_max_can_be_used;

import java.util.Comparator;

class ComparatorMinMaxCanBeUsed {
  void testGreaterThan(Comparator<String> comp, String a, String b) {
    String r1 = <warning descr="Can be replaced with 'Comparator.max()'"><caret>comp.compare(a, b) > 0 ? a : b</warning>;
    String r2 = <warning descr="Can be replaced with 'Comparator.min()'">comp.compare(a, b) > 0 ? b : a</warning>;
  }

  void testLessThan(Comparator<String> comp, String a, String b) {
    String r1 = <warning descr="Can be replaced with 'Comparator.min()'">comp.compare(a, b) < 0 ? a : b</warning>;
    String r2 = <warning descr="Can be replaced with 'Comparator.max()'">comp.compare(a, b) < 0 ? b : a</warning>;
  }

  void testGreaterThanOrEqual(Comparator<String> comp, String a, String b) {
    String r1 = <warning descr="Can be replaced with 'Comparator.max()'">comp.compare(a, b) >= 0 ? a : b</warning>;
    String r2 = <warning descr="Can be replaced with 'Comparator.min()'">comp.compare(a, b) >= 0 ? b : a</warning>;
  }

  void testLessThanOrEqual(Comparator<String> comp, String a, String b) {
    String r1 = <warning descr="Can be replaced with 'Comparator.min()'">comp.compare(a, b) <= 0 ? a : b</warning>;
    String r2 = <warning descr="Can be replaced with 'Comparator.max()'">comp.compare(a, b) <= 0 ? b : a</warning>;
  }

  void testReversedComparison(Comparator<String> comp, String a, String b) {
    // 0 < compare(a, b) is equivalent to compare(a, b) > 0
    String r1 = <warning descr="Can be replaced with 'Comparator.max()'">0 < comp.compare(a, b) ? a : b</warning>;
    // 0 >= compare(a, b) is equivalent to compare(a, b) <= 0
    String r2 = <warning descr="Can be replaced with 'Comparator.max()'">0 >= comp.compare(a, b) ? b : a</warning>;
  }

  void testParenthesized(Comparator<String> comp, String a, String b) {
    String r1 = <warning descr="Can be replaced with 'Comparator.max()'">(comp.compare(a, b)) > 0 ? a : b</warning>;
    String r2 = <warning descr="Can be replaced with 'Comparator.min()'">comp.compare(a, b) > 0 ? (b) : (a)</warning>;
  }

  // If-statement patterns
  String testIfReturnGreaterThan(Comparator<String> comp, String a, String b) {
    <warning descr="Can be replaced with 'Comparator.max()'">if</warning> (comp.compare(a, b) > 0) return a; else return b;
  }

  String testIfReturnLessThan(Comparator<String> comp, String a, String b) {
    <warning descr="Can be replaced with 'Comparator.min()'">if</warning> (comp.compare(a, b) < 0) return a; else return b;
  }

  String testIfReturnReversedBranches(Comparator<String> comp, String a, String b) {
    <warning descr="Can be replaced with 'Comparator.min()'">if</warning> (comp.compare(a, b) > 0) return b; else return a;
  }

  String testIfReturnWithBlocks(Comparator<String> comp, String a, String b) {
    <warning descr="Can be replaced with 'Comparator.max()'">if</warning> (comp.compare(a, b) > 0) {
      return a;
    } else {
      return b;
    }
  }

  String testIfImplicitReturn(Comparator<String> comp, String a, String b) {
    <warning descr="Can be replaced with 'Comparator.max()'">if</warning> (comp.compare(a, b) > 0) return a;
    return b;
  }

  void testIfAssignment(Comparator<String> comp, String a, String b) {
    String r;
    <warning descr="Can be replaced with 'Comparator.max()'">if</warning> (comp.compare(a, b) > 0) r = a; else r = b;
    System.out.println(r);
  }

  void testIfOverwrittenDeclaration(Comparator<String> comp, String a, String b) {
    String r = b;
    <warning descr="Can be replaced with 'Comparator.max()'">if</warning> (comp.compare(a, b) > 0) r = a;
    System.out.println(r);
  }

  void testIfGreaterOrEqual(Comparator<String> comp, String a, String b) {
    String r;
    <warning descr="Can be replaced with 'Comparator.max()'">if</warning> (comp.compare(a, b) >= 0) r = a; else r = b;
    System.out.println(r);
  }

  void testIfReversedComparison(Comparator<String> comp, String a, String b) {
    String r;
    <warning descr="Can be replaced with 'Comparator.max()'">if</warning> (0 < comp.compare(a, b)) r = a; else r = b;
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
    String r1 = <warning descr="Can be replaced with 'Comparator.max()'">comp.compare(a.trim(), b.trim()) > 0 ? a.trim() : b.trim()</warning>;
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
