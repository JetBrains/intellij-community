import org.jetbrains.annotations.Nullable;
import java.util.Optional;

public class OptionalInlining {
  void testOrElse() {
    String s = Optional.ofNullable(<warning descr="Passing a non-null argument to 'Optional'">"foo"</warning>).orElse("bar");
    if (<warning descr="Condition 's.equals(\"bar\")' is always 'false'">s.equals("bar")</warning>) {
      System.out.println("Never");
    }
    String s2 = Optional.<String>ofNullable(<warning descr="Passing 'null' argument to 'Optional'">null</warning>).orElse("bar");
    if (<warning descr="Condition 's2.equals(\"bar\")' is always 'true'">s2.equals("bar")</warning>) {
      System.out.println("Always");
    }
    String s3 = Optional.of(Math.random() > 0.5 ? "foo" : "baz").orElse("bar");
    if (<warning descr="Condition 's3.equals(\"foo\") || s3.equals(\"baz\")' is always 'true'">s3.equals("foo") || s3.equals("baz")</warning>) {
      System.out.println("Always");
    }
    if (<warning descr="Condition 's3.equals(\"bar\")' is always 'false'">s3.equals("bar")</warning>) {
      System.out.println("Never");
    }
  }

  void testIsPresent(Optional<String> opt) {
    if (<warning descr="Condition '!opt.isPresent() && opt.orElse(\"foo\").equals(\"bar\")' is always 'false'">!opt.isPresent() && <warning descr="Condition 'opt.orElse(\"foo\").equals(\"bar\")' is always 'false' when reached">opt.orElse("foo").equals("bar")</warning></warning>) {

    }
  }

  void testDeref(Optional<String> opt) {
    if (opt == null) {
      System.out.println(<warning descr="Dereference of 'opt' may produce 'java.lang.NullPointerException'">opt</warning>.orElse("qq"));
    }
  }

  void testOrElseGet(Optional<String> opt) {
    opt.orElseGet(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    String s = opt.orElseGet(() -> {
      if (Math.random() > 0.5) {
        return "foo";
      }
      return "baz";
    });
    if (<warning descr="Condition 's.equals(\"bar\") && !opt.isPresent()' is always 'false'">s.equals("bar") && <warning descr="Condition '!opt.isPresent()' is always 'false' when reached">!<warning descr="Condition 'opt.isPresent()' is always 'true' when reached">opt.isPresent()</warning></warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testFilter(Optional<String> opt, Optional<Integer> intOpt) {
    opt.filter(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    Integer integer = intOpt.filter(x -> x > 5).filter(x -> <warning descr="Condition 'x == 5' is always 'false'">x == 5</warning>).orElse(0);
    String s1 = opt.filter(s -> false).filter(s -> s.equals("barr")).orElse("baz");
    if (<warning descr="Condition 's1.equals(\"xz\")' is always 'false'">s1.equals("xz")</warning>) {
      System.out.println("never");
    }
    String abc = opt.filter(s -> s == "xyz").orElse("abc"); // s.equals("xyz") does not work yet :(
    if (<warning descr="Condition 'abc.equals(\"123\")' is always 'false'">abc.equals("123")</warning>) {
      System.out.println("never");
    }
    if (abc.equals("xyz") && <warning descr="Condition 'opt.isPresent()' is always 'true' when reached">opt.isPresent()</warning>) {
      System.out.println("always");
    }
  }

  @Nullable
  String nullableMethod() {
    if(Math.random() > 0.5) {
      return null;
    }
    return "";
  }

  @Nullable
  Object getObj(String s) {
    return new Object();
  }

  void testMap(Optional<String> opt) {
    opt.map(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    String res = opt.<String>map(s -> null).orElse("abc");
    if (<warning descr="Condition '!res.equals(\"abc\")' is always 'false'">!res.equals("abc")</warning>) {
      System.out.println("Never");
    }
    String trimmed = Optional.ofNullable(nullableMethod()).map(xx -> xx.trim()).orElse("");
    if(<warning descr="Condition 'trimmed == null' is always 'false'">trimmed == null</warning>) {
      System.out.println("impossible");
    }
    String xyz = nullableMethod();
    Object n = Optional.ofNullable(xyz).map(String::trim).map(this::getObj).orElse(null);
    if(n instanceof Integer) {
      // n instanceof Integer -> n is not null -> xyz was not null -> safe to dereference
      System.out.println(xyz.trim());
    }
    xyz.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>();
  }

  void testFlatMap(Optional<String> opt) {
    opt.flatMap(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    String s = opt.flatMap(str -> Optional.of(str.length() > 10 ? "foo" : "bar")).orElse("baz");
    if (<warning descr="Condition 's.equals(\"qux\")' is always 'false'">s.equals("qux")</warning>) {
      System.out.println("Never");
    }
  }

  void testIfPresent(Optional<String> opt) {
    opt.ifPresent(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    opt.map(s -> s.isEmpty() ? 5 : 6).ifPresent(val -> {
      if (val == 7) {
        System.out.println("oops");
      }
    });
  }

  void testIntermediate(Optional<String> opt) {
    if (<warning descr="Condition 'opt.filter(x -> x == \"foo\").filter(x -> x == \"bar\").isPresent()' is always 'false'">opt.filter(x -> x == "foo").filter(x -> <warning descr="Condition 'x == \"bar\"' is always 'false'">x == "bar"</warning>).isPresent()</warning>) {
      System.out.println("never");
    }
  }

  // IDEA-174759
  void testTwoOptionalInteraction(Optional<String> a, Optional<String> b) {
    if (a.isPresent() || b.isPresent()) {
      // prefer a over b
      Integer result = a.map(s -> s + "0").map(s -> Integer.parseInt(s))
        .orElseGet(() -> Integer.parseInt(b.get())); // <-- no more warning for b.get()
      System.out.println(result);
    }
  }

  void testTwoOptionalInteractionMethodRef(Optional<String> a, Optional<String> b) {
    if (a.isPresent() || b.isPresent()) {
      // prefer a over b
      Integer result = a.map(s -> s + "0").map(Integer::parseInt)
        .orElseGet(() -> Integer.parseInt(b.get())); // <-- no more warning for b.get()
      System.out.println(result);
    }
  }

  static class Holder {
    int x;
    String s;
  }

  void testFilterChain(Optional<Holder> opt) {
    boolean present = <warning descr="Condition 'opt .filter(h -> h.x < 5) .filter(h -> h.x > 6) .map(h -> h.x).isPresent()' is always 'false'">opt
        .filter(h -> h.x < 5)
        .filter(h -> <warning descr="Condition 'h.x > 6' is always 'false'">h.x > 6</warning>)
        .map(h -> h.x).isPresent()</warning>;
  }

  void testFilterMap(Optional<Holder> opt) {
    boolean present = <warning descr="Condition 'opt .filter(h -> h.s == null) .map(h -> h.s) .isPresent()' is always 'false'">opt
      .filter(h -> h.s == null)
      .map(h -> h.s)
      .isPresent()</warning>;

    opt.filter(h -> h.s == null).map(h -> h.s.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>()).ifPresent(System.out::println);
  }
}