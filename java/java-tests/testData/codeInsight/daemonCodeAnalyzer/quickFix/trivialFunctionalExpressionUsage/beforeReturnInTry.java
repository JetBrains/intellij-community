// "Replace method call on lambda with lambda body" "false"
class Test {
  public static void main(String[] args) {
    for (int i = 0; i < 10; i++) {
      final int finalI = i;
      ((Runnable) () -> {
        try {
          if (finalI > 3) {
            return;
          }
          System.out.println(finalI);
        } catch (Exception ignored) {
        }
      }).ru<caret>n();
    }
    System.out.println("Hello");
  }
}