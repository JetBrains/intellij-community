import org.jetbrains.annotations.NotNull;

public class Test {

  void test() {
    String first = getFirst();
    String second = getFirst();
    String third = "Third";
  }

    @NotNull
    private static String getFirst() {
        return "First";
    }
}