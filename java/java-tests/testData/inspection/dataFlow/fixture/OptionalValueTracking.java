import java.util.*;

class Main {
  void testGetNonNullWithVar() {
    Optional<? extends String> opt = getOptional("");
    if (!opt.isPresent()) return;
    System.out.println(opt.get().trim());
  }
  
  void testGetNonNull() {
    System.out.println(getOptional("x").get().trim());
  }

  void simple(String s) {
    Optional<String> opt = Optional.of("foo");
    if (<warning descr="Condition 'opt.get().equals(\"foo\")' is always 'true'">opt.get().equals("foo")</warning>) {}
    Optional<String> opt2 = Optional.ofNullable(s);
    if (opt2.isPresent() && <warning descr="Condition 's != null' is always 'true' when reached">s != null</warning>) {}
    if ("foo".equals(s) && <warning descr="Condition 'opt2.map(str -> str.substring(1)).orElse(\"bar\").equals(\"oo\")' is always 'true' when reached">opt2.map(str -> str.substring(1)).orElse("bar").equals("oo")</warning>) {}
    if (opt2.isPresent() && <warning descr="Condition 'opt2.get().equals(s)' is always 'true' when reached">opt2.get().equals(s)</warning>) {}
  }
  
  native Optional<String> getOptional(String foo);
}