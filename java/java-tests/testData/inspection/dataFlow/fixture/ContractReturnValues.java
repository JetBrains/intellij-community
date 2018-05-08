import java.util.*;
import org.jetbrains.annotations.*;

class ContractReturnValues {
  void testRequireNonNull() {
    System.out.println(Objects.requireNonNull(nullable()).trim());
    System.out.println(nullable().<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>());
  }

  void test(StringBuilder sb) {
    StringBuilder sb1 = sb.append("foo");
    if (<warning descr="Condition 'sb1 == sb' is always 'true'">sb1 == sb</warning>) {
      System.out.println("Always");
    }
  }

  void test2(Object o) {
    if(<warning descr="Condition 'Objects.requireNonNull(o) != o' is always 'false'">Objects.requireNonNull(o) != o</warning>) {
      System.out.println("???");
    }
  }

  void testLocality(int[] data) {
    int[] clone = Arrays.copyOf(data, 10);
    clone[1] = 5;
    data[1] = 5;
    unknown();
    if(data[1] == 5) {
      System.out.println("who knows? unknown() could modify data");
    }
    if(<warning descr="Condition 'clone[1] == 5' is always 'true'">clone[1] == 5</warning>) {
      System.out.println("always");
    }
  }

  native void unknown();

  @Nullable
  native String nullable();

  void testNewGetter() {
    Object x = getXyz();
    if(<warning descr="Condition 'x != null' is always 'true'">x != null</warning>) {
      System.out.println("always");
    }
  }

  static Object getXyz() {
    return new Object();
  }

  void testNullToEmpty(String s, String s1) {
    if(nullToEmpty(s) != s) {
      System.out.println(<warning descr="Condition 's == null' is always 'true'">s == null</warning>);
    }
    if(<warning descr="Condition 'nullToEmpty(s1) == null' is always 'false'">nullToEmpty(s1) == null</warning>) {
      System.out.println("never");
    }
  }

  static String nullToEmpty(@Nullable String s) {
    return s == null ? "" : s;
  }

  void testNullToSomething() {
    System.out.println(nullToSomething(nullable()).trim());
    System.out.println(nullToEmpty(nullable()).trim());
  }

  static String nullToSomething(@Nullable String s) {
    return s == null ? SOMETHING : s;
  }

  private static final String SOMETHING = "foo";

  void testCheckString() {
    String s2 = nullable();
    checkString(s2);
    if (<warning descr="Condition 's2 == null' is always 'false'">s2 == null</warning>) {
      System.out.println("impossible");
    }
    String s = nullable();
    if (<warning descr="Condition 'checkString(s) == null' is always 'false'">checkString(s) == null</warning>) {
      System.out.println("impossible");
    }
  }

  // inferred contract: "_ -> param1"
  // we should respect "notnull" even if we cannot infer failing contract
  @NotNull
  static String checkString(@Nullable String s) {
    check(s); // probably does a null-check
    return s;
  }

  @Contract("null -> fail")
  native static void check(@Nullable String s);
}
