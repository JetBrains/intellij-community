import java.util.Optional;

class Test {
  private void checkIsPresent(boolean b) {
    Optional<String> test;
    if (b) {
      test = Optional.of("x");
    } else {
      test = Optional.empty();
      if(<warning descr="Condition '!test.isPresent()' is always 'true'">!<warning descr="Condition 'test.isPresent()' is always 'false'">test.isPresent()</warning></warning>) {
        System.out.println("Always");
      }
    }
    Optional<String> other = test;
    if(test.isPresent() && <warning descr="Condition 'other.isPresent()' is always 'true' when reached">other.isPresent()</warning>) {
      System.out.println(test.get());
    }
  }

  void m(Optional<Integer> maybe) {
    if (!!!maybe.isPresent()) {
      maybe = getIntegerOptional();
    }
    else {
      System.out.println(maybe.get());
      maybe = getIntegerOptional();
    }
    if (maybe.isPresent()) {
      maybe = Optional.empty();
      System.out.println(maybe.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>());
    }
    boolean b = <warning descr="Condition '((maybe.isPresent())) && maybe.get() == 1' is always 'false'"><warning descr="Condition '((maybe.isPresent()))' is always 'false'">((<warning descr="Condition 'maybe.isPresent()' is always 'false'">maybe.isPresent()</warning>))</warning> && maybe.get() == 1</warning>;
    boolean c = <warning descr="Condition '(!maybe.isPresent()) || maybe.get() == 1' is always 'true'"><warning descr="Condition '(!maybe.isPresent())' is always 'true'">(<warning descr="Condition '!maybe.isPresent()' is always 'true'">!<warning descr="Condition 'maybe.isPresent()' is always 'false'">maybe.isPresent()</warning></warning>)</warning> || maybe.get() == 1</warning>;
    Integer value = <warning descr="Condition '!maybe.isPresent()' is always 'true'">!<warning descr="Condition 'maybe.isPresent()' is always 'false'">maybe.isPresent()</warning></warning> ? 0 : maybe.get();
  }

  Optional<Integer> getIntegerOptional() {
    return Math.random() > 0.5 ? Optional.of(1) : Optional.empty();
  }

  private static void a() {
    Optional<String> optional = Optional.empty();
    final boolean present = <warning descr="Condition 'optional.isPresent()' is always 'false'">optional.isPresent()</warning>;
    // optional = Optional.empty();
    if (<warning descr="Condition 'present' is always 'false'">present</warning>) {
      final String string = optional.get();
      System.out.println(string);
    }
  }

  private static void b() {
    Optional<String> optional = Optional.empty();
    final boolean present = <warning descr="Condition 'optional.isPresent()' is always 'false'">optional.isPresent()</warning>;
    optional = Optional.empty();
    if (<warning descr="Condition 'present' is always 'false'">present</warning>) {
      final String string = optional.get();
      System.out.println(string);
    }
  }

  public void testMultiVars(Optional<String> opt) {
    boolean present = opt.isPresent();
    boolean absent = !present;
    boolean otherAbsent = !!absent;
    if(otherAbsent) {
      System.out.println(opt.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>());
    } else {
      System.out.println(opt.get());
    }
  }

  public static Optional<String> getOptional() {
    return Optional.empty();
  }

  private void checkAsserts1() {
    Optional<String> o2 = getOptional();
    org.junit.Assert.assertTrue(!o2.isPresent());
    System.out.println(o2.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>());
  }

  private void checkAsserts2() {
    Optional<String> o3 = Optional.empty();
    org.testng.Assert.<warning descr="The call to 'assertTrue' always fails, according to its method contracts">assertTrue</warning>(<warning descr="Condition 'o3.isPresent()' is always 'false'">o3.isPresent()</warning>);
    System.out.println(o3.get());
  }

  private void checkOf(boolean b) {
    System.out.println(Optional.of("xyz").get());
    Optional<String> test;
    if(b) {
      test = Optional.empty();
    } else {
      test = Optional.empty();
    }
    System.out.println(test.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>());

  }

  public static String demo() {
    Optional<String> holder = Optional.empty();

    if (<warning descr="Condition '! holder.isPresent()' is always 'true'">! <warning descr="Condition 'holder.isPresent()' is always 'false'">holder.isPresent()</warning></warning>) {
      holder = Optional.of("hello world");
      if (<warning descr="Condition '!holder.isPresent()' is always 'false'">!<warning descr="Condition 'holder.isPresent()' is always 'true'">holder.isPresent()</warning></warning>) {
        return null;
      }
    }

    return holder.get();
  }

  void order(Optional<String> order, boolean b) {
    order.ifPresent(o -> System.out.println(order.get()));
    System.out.println(order.orElseGet(() -> order.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>().trim()));
  }

  void guavaTest(com.google.common.base.Optional<String> opt, String s, String s1) {
    System.out.println(opt.get());
    if(<warning descr="Condition 'opt.isPresent()' is always 'true'">opt.isPresent()</warning>) {
      System.out.println(opt.get());
    }
    opt = com.google.common.base.Optional.fromNullable(s);
    if(opt.isPresent()) {
      System.out.println(opt.get());
    }
    opt = com.google.common.base.Optional.of(<warning descr="Argument 's' might be null">s</warning>);
    opt = com.google.common.base.Optional.of(s1);
    if(<warning descr="Condition 'opt.isPresent()' is always 'true'">opt.isPresent()</warning>) {
      System.out.println(opt.get());
    }
    opt = com.google.common.base.Optional.absent();
    if(<warning descr="Condition 'opt.isPresent()' is always 'false'">opt.isPresent()</warning>) {
      System.out.println(opt.get());
    }
  }

  void testThrow2(Optional<String> test) {
    test.orElseThrow(RuntimeException::new);
    if (<warning descr="Condition 'test.isPresent()' is always 'true'">test.isPresent()</warning>) {
      System.out.println("Yes");
    }
  }

  public void testThrowFail(Optional<String> arg) {
    if(!arg.isPresent()) {
      System.out.println(arg.<warning descr="The call to 'orElseThrow' always fails, according to its method contracts">orElseThrow</warning>(IllegalAccessError::new));
    }
    String res = Optional.<String>empty().<warning descr="The call to 'orElseThrow' always fails, according to its method contracts">orElseThrow</warning>(RuntimeException::new);
  }
}
