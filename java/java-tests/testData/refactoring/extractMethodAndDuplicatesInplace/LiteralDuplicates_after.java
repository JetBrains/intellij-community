import org.jetbrains.annotations.NotNull;

public class Test {

  void test() {
    String first = getFirst();
    String second = getFirst();
    String third = "Third";
  }

    private static @NotNull String getFirst() {
        return "First";
    }
}