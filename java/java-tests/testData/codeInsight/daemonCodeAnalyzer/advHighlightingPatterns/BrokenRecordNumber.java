public class BrokenRecordNumber {

  sealed interface UA {
    public record UA1(A1 a1) implements UA {
    }

    public record UA2(A1 a1, A2 a2) implements UA {
    }
  }

  sealed interface A1 {
    final class A11 implements A1 {
    }

    final class A12 implements A1 {
    }
  }

  sealed interface A2 {
    final class A21 implements A2 {
    }

    final class A22 implements A2 {
    }
  }

  public static void main(String[] args) {

  }

  public static void test(UA ua) {
    switch (ua) {
      case UA.UA2(A1.A11 a1, A2.A21 a2) -> System.out.println("UA2");
      case UA.UA1(A1.A11 a1) -> System.out.println("UA1");
      case UA.UA1(A1.A12 a1) -> System.out.println("UA1");
      case UA.UA2(A1 a1, A2.A21 a2) -> System.out.println("UA2");
      case UA.UA2(A1.A12 a1, A2.A22 a2) -> System.out.println("UA2");
      case UA.UA2(A1.A11 a1, A2.A22 a2) -> System.out.println("UA2");
    }
  }

  public static void test3(UA ua) {
    switch (ua) {
      case UA.UA2<error descr="Incorrect number of nested patterns: expected 2 but found 1">(A1.A11 a1)</error> -> System.out.println("UA2");
      case UA.UA1(A1.A11 a1) -> System.out.println("UA1");
      case UA.UA1(A1.A12 a1) -> System.out.println("UA1");
      case UA.UA2(A1 a1, A2.A21 a2) -> System.out.println("UA2");
      case UA.UA2(A1.A12 a1, A2.A22 a2) -> System.out.println("UA2");
      case UA.UA2(A1.A11 a1, A2.A22 a2) -> System.out.println("UA2");
    }
  }

  public static void test4(UA ua) {
    switch (ua) {
      case UA.UA1(A1.A11 a1) -> System.out.println("UA1");
      case UA.UA1(A1.A12 a1) -> System.out.println("UA1");
      case UA.UA2(A1 a1, A2.A21 a2) -> System.out.println("UA2");
      case UA.UA2(A1.A12 a1, A2.A22 a2) -> System.out.println("UA2");
      case UA.UA2(A1.A11 a1, A2.A22 a2) -> System.out.println("UA2");
    }
  }

  public static void test2(UA ua) {
    switch (ua) {
      case UA.UA2(A1.A11 a1, A2.A21 a2) -> System.out.println("UA2");
      case UA.UA1(A1.A11 a1) -> System.out.println("UA1");
      case UA.UA1(A1.A12 a1) -> System.out.println("UA1");
      case UA.UA2<error descr="Incorrect number of nested patterns: expected 2 but found 1">(A1 a1)</error> -> System.out.println("UA2");
      case UA.UA2(A1.A12 a1, A2.A22 a2) -> System.out.println("UA2");
      case UA.UA2(A1.A11 a1, A2.A22 a2) -> System.out.println("UA2");
    }
  }
}
