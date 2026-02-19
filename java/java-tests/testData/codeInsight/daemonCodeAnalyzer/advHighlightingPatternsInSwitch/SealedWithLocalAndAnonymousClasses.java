public class SealedWithLocalAndAnonymousClasses {

  sealed interface I {
    final class C1 implements I {
    }

    static void test() {
      final class TT implements <error descr="Local classes must not extend sealed classes">I</error> {

      }

      I i2 = new <error descr="Anonymous classes must not extend sealed classes">I</error>() {
      };

      I i = getI();
      switch (i) {
        case C1 c1 -> {
          System.out.println("1");
        }
      }
    }

    private static I getI() {
      return null;
    }
  }
}