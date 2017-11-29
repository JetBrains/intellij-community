// "Replace method call on lambda with lambda body" "false"

public class Main {
  public static void main(String[] args) {
    for (int i = 0; i < 10; ((Runnable) (() -> {
      System.out.println("Hello");
      System.out.println("World");
      return;
    })).ru<caret>n()) {
      System.out.println(i++);
    }
  }
}