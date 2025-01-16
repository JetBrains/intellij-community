// "Replace with <>" "false"

import java.util.List;

class XYZ {
  @Target({ElementType.TYPE_USE, ElementType.METHOD})
  public @interface Nullable {}

  public final static class Wrapper<T> {
    private final T value;

    public Wrapper(T value) {
      this.value = value;
    }
  }

  public static List<@Nullable String> getStrings() {
    String element = ThreadLocalRandom.current().nextBoolean() ? "hello" : null;
    return Collections.singletonList(element);
  }

  public static <T> void genericConsumer(T item) {}

  public static void main(String[] args) {
    // suggests to replace the explicit type below with <>
    genericConsumer(new Wrapper<L<caret>ist<@Nullable String>>(getStrings()));
    // below one works great
    Wrapper<List<@Nullable String>> wrapper = new Wrapper<>(getStrings());
  }
}