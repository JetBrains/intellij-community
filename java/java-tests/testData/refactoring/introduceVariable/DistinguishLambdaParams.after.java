import java.util.function.UnaryOperator;

class Main {
  public static void main(String[] args) {
    UnaryOperator<String> f1 = s -> {
      System.out.println("foo");
        String temp = s.trim();
        return temp;
    };
    UnaryOperator<String> f2 = s -> {
      System.out.println("foo");
      return s.trim();
    };
  }
}