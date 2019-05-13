
import java.util.*;
import java.util.function.Consumer;

class Test {

  public void foo(final Logic<String> logic, Set<Map<String, String>> models) {
    final Consumer<Map<String, String>> action = logic::declareField;
  }

  private static class Logic<E> {
    public <T> void declareField(Map<E, T> field) {}
  }

}
