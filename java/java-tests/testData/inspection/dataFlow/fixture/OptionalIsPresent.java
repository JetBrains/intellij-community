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
}
