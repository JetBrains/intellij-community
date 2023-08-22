import org.jetbrains.annotations.NotNull;

class Test {
  void test(){
    System.out.println(getString() + "three");
  }

    @NotNull
    private static String getString() {
        return "one" + "two";
    }
}