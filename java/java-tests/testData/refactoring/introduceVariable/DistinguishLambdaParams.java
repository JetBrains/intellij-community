import java.util.function.UnaryOperator;

class Main {
  public static void main(String[] args) {
    UnaryOperator<String> f1 = s -> {
      System.out.println("foo");
      return <selection>s.trim()</selection>;
    };
    UnaryOperator<String> f2 = s -> {
      System.out.println("foo");
      return s.trim();
    };
  }
}