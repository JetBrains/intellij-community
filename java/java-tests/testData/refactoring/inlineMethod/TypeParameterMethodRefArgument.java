public class Test {
  @FunctionalInterface
  public interface Sup<T> {
    T make();
  }

  public String method() {
    return <caret>inlineMe(this::toString);
  }

  private static <T> T inlineMe(Sup<T> sup) {
    return sup.make();
  }
}