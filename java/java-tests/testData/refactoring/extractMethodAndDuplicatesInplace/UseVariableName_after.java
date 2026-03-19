import org.jetbrains.annotations.NotNull;

class X {
  void test() {
      String greeting = getGreeting();
      System.out.println(greeting);
  }

    private static @NotNull String getGreeting() {
        String greeting = "Hello World!"; // extract this line
        return greeting;
    }
}