public class Main {

  sealed interface I {
    final class C1 implements I {
    }

    static void test() {
      final class TT implements I {

      }

      I i2 = new I() {
      };

      I i = getI();
      switch (i) {
        <caret>
      }
    }

    private static I getI() {
      return null;
    }
  }
}