// "Replace with <>" "false"
class XYZ {
  @Target({ElementType.TYPE_USE, ElementType.METHOD})
  public @interface Nullable {}

  public final static class Wrapper<T> {
    private final T value;

    public Wrapper(T value) {
      this.value = value;
    }
  }
  @Nullable
  public static String getString() {
    return ThreadLocalRandom.current().nextBoolean() ? "hello" : null;
  }

  public static <T> void genericConsumer(T item) {}

  public static void main(String[] args) {
    genericConsumer(new Wrapper<@Nullable<caret> String>(getString()));
    // below one works great
    Wrapper<@Nullable String> wrapper = new Wrapper<>(getString());
  }
}