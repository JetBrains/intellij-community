// "Replace method call on lambda with lambda body" "false"

public class Main {
  public static void main(String[] args) {
    int i = 0;
    for (((Runnable) (() -> {
      System.out.println("Hello");
      System.out.println("World");
    })).r<caret>un(); i < 10; ) {
      System.out.println(i++);
    }
  }
}