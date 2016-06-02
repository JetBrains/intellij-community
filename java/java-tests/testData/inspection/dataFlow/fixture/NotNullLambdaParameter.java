import org.jetbrains.annotations.NotNull;
class Example {
  interface Listener {
    void notify(@NotNull String value);
  }

  public static void main(String[] args) {
    Listener l = value -> {
      if (<warning descr="Condition 'value != null' is always 'true'">value != null</warning>) {
        System.out.println(value);
      }
    };
  }
}
