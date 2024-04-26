import org.jetbrains.annotations.NotNull;

class Test {
  void test(){
    System.out.println(getString() + "three");
  }

    private static @NotNull String getString() {
        return "one" + "two";
    }
}