import org.jetbrains.annotations.NotNull;

class Test {
  void test(){
    System.out.println("one" + getTwo() + "three");
    System.out.println("one " + getTwo() + " three");
  }

    private static @NotNull String getTwo() {
        return "two";
    }
}