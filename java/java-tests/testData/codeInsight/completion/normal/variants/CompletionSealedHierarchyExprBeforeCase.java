public class Main {


  sealed interface I {
  }

  record R(String string) implements I {
  }

  record R2(String string) implements I {
  }

  public static void main(String[] args) {
    test(new R("1"));
  }

  public static void test(I i) {
    switch (i) {
      case R(String s) -> {
        System.out.println(1 + s);
      }
      ca<caret>
      case R r -> {
        System.out.println(2);
      }
      default -> {
        System.out.println(3);
      }
    }
  }
}