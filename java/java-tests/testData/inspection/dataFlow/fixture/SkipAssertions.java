import org.jetbrains.annotations.NotNull;

class Test {
  private static void test(@NotNull Object foo) {
    assert foo != null;
  }

  private static void testParens(@NotNull Object foo) {
    assert (foo != null);
  }

  private static void testNegation(boolean b) {
    if(!b) {
      assert !b;
    }
  }

  private static void testAnd(boolean a, boolean b, boolean c) {
    if(b) {
      assert a && b && c;
    }
  }

  private static void testOr(boolean a, boolean b, boolean c) {
    if(b) {
      // c is never checked: probably not intended; report
      assert a || <warning descr="Condition 'b' is always 'true'">b</warning> || c;

      assert a || c || b;
    }
  }

  private static void testOrNot(boolean a, boolean b, boolean c) {
    if(!b) {
      assert !(a || b || c);
    }
  }

  private static void testOrNotFail(boolean a, boolean b, boolean c) {
    if(b) {
      assert <warning descr="Condition '!(a || b || c)' is always 'false'">!(a || <warning descr="Condition 'b' is always 'true'">b</warning> || c)</warning>;
    }
  }

  private static void testAndNotFail(boolean a, boolean b, boolean c) {
    if(b) {
      assert !(a && <warning descr="Condition 'b' is always 'true'">b</warning> && c);
    }
  }

  private static void testInstanceOf(java.util.List<String> list) {
    Object s = list.get(0);
    System.out.println(s.hashCode());
    assert s instanceof String;
    assert <warning descr="Condition 'list.get(1) instanceof String' is redundant and can be replaced with a null check">list.get(1) instanceof String</warning>;
    assert <warning descr="Condition 's instanceof Number' is always 'false'">s instanceof Number</warning>;
  }

  private static void test2(@NotNull Object foo) {
    if (foo == null) {
      throw new IllegalArgumentException();
    }
  }
  private static void test3(@NotNull Object foo) {
    if (foo == null) throw new IllegalArgumentException();
  }

  private static void test4(@NotNull Object foo) {
    if (<warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>) throw new IllegalArgumentException();
  }

  private static void assertMethod(@NotNull Object foo) {
    assertTrue(foo != null);
    assertFalse(foo == null);
  }

  private static void assertMethodFail(@NotNull Object foo) {
    <warning descr="The call to 'assertTrue' always fails, according to its method contracts">assertTrue</warning>(<warning descr="Condition 'foo == null' is always 'false'">foo == null</warning>);
  }

  static final void assertTrue(boolean x) {
    if(!x) throw new AssertionError();
  }

  static final void assertFalse(boolean x) {
    if(x) throw new AssertionError();
  }
}