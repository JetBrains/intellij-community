// "Replace method call on lambda with lambda body" "true-preview"

public class Main {
  public static void main(String[] args) {
    int i = 0;
      System.out.println("Hello");
      System.out.println("World");
      for (; i < 10; ) {
      System.out.println(i++);
    }
  }
}