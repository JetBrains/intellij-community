// "Replace lambda with method reference (may change semantics)" "true-preview"
import java.util.*;
import java.util.function.Predicate;

class Main {
  public static void main(String[] args) {
    final Test test = new Test();
    final List<Sub> subs = Arrays.asList(new Sub(), new Sub());

    subs.forEach(t -> test.getSubs().a<caret>dd(t));
  }

  private static class Test {
    private final List<Sub> subs = new ArrayList<>();

    private List<Sub> getSubs() {
      return subs;
    }
  }

  private static class Sub {
  }
}
