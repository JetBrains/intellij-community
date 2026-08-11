package dfa;

public class MixFloatingPointWithIntegralLiterals {
  private static float aaa = 2;

  static void main(String[] args) {
    switch (1.0) {
      case <error descr="Incompatible types. Found: 'int', required: 'double'">1</error> -> System.out.println(1);
      default -> System.out.println(2);
    }
    switch (1.0) {
      case 1.0 -> System.out.println(1);
      default -> System.out.println(2);
    }
    switch (1.0) {
      case <error descr="Incompatible types. Found: 'char', required: 'double'">'c'</error> -> System.out.println(1);
      default -> System.out.println(2);
    }
    switch (1.0f) {
      case <error descr="Incompatible types. Found: 'int', required: 'float'">1</error> -> System.out.println(1);
      default -> System.out.println(2);
    }
    switch (1.0f) {
      case <error descr="Constant expression required">aaa</error> -> System.out.println(1);
      default -> System.out.println(2);
    }
  }
}
