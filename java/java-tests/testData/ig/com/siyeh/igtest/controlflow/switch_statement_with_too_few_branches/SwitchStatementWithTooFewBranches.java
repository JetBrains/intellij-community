package com.siyeh.igtest.controlflow.switch_statement_with_too_few_branches;

class SwitchStatementWithTooFewBranches {

  void foo(int i) {
    switch (i) {}
    <warning descr="'switch' statement has too few case labels (1), and should probably be replaced with an 'if' statement">switch</warning> (i) {
      case 1:
        System.out.println(i);
    }
    <warning descr="'switch' statement has too few case labels (1), and should probably be replaced with an 'if' statement">switch</warning>(i) {
      case 1:
        System.out.println(1);
    }
    <warning descr="'switch' statement has only 'default' case">switch</warning>(i) {
      default:
        System.out.println(2);
    }
    switch(i) {
      case 1:
        System.out.println(1);
      case 2:
        System.out.println(2);
    }
    switch(i) {
      case 1:
        System.out.println(1);
      case 2:
        System.out.println(2);
      default:
        System.out.println(3);
    }
    switch(i) {
      case 1:
        break;
      case 2:
        break;
      case 3:
        break;

    }
    <warning descr="'switch' statement has too few case labels (1), and should probably be replaced with an 'if' statement">switch</warning> (i) {
      case 1 -> {}
    }
    switch (i) {
      case 1,2 -> {}
    }
    switch (i) {
      case 1 -> {}
      case 2 -> {}
    }
    <warning descr="'switch' statement has only 'default' case">switch</warning> (i) {
      default -> {}
    }

    System.out.println(<warning descr="'switch' expression has only 'default' case">switch</warning>(i) {default -> "foo";});
    System.out.println(<warning descr="'switch' expression has too few case labels (1), and should probably be replaced with an 'if' statement or conditional operator">switch</warning>(i) {case 1 -> "bar"; default -> "foo";});
    System.out.println(switch(i) {case 1,2 -> "bar"; default -> "foo";});
    System.out.println(switch(i) {case 1 -> "bar"; case 2 -> "baz"; default -> "foo";});
    
    switch (i)<EOLError descr="'{' expected"></EOLError>

    Object o = new Object();
    switch (o) {
      case null -> System.out.println();
      case String s -> {
        System.out.println(s);
      }
      default -> System.out.println();
    }

    <warning descr="'switch' statement has only 'default' case">switch</warning> (o) {
      default -> System.out.println();
    }

    <warning descr="'switch' statement has too few case labels (1), and should probably be replaced with an 'if' statement">switch</warning> (o) {
      case null -> System.out.println();
      default -> System.out.println();
    }
  }

  void test(ONE one) {
    int x = switch(one) {
      case A -> 1;
    };
    int y = <warning descr="'switch' expression has too few case labels (1), and should probably be replaced with an 'if' statement or conditional operator">switch</warning>(one) {
      case A -> 1;
      default -> 0;
    };
  }

  enum ONE { A }
}
