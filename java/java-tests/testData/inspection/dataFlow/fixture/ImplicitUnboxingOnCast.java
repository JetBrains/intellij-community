import org.jetbrains.annotations.Nullable;

// IDEA-229498
class Test {
  public void testGet() {
    int x = (int) <warning descr="Unboxing of 'get(\"foo\")' may produce 'NullPointerException'">get("foo")</warning>;
  }
  @Nullable
  public Object get(Object t) {
    return null;
  }
}