import java.util.function.BooleanSupplier;

class Test {
  void bar() {
    foo(new BooleanSupplier() {
        public boolean getAsBoolean() {
            if (1 > 0) {
                System.out.println(1);
                System.out.println(1);
                return true;
            }
            return false;
        }
    });
  }

  void foo(BooleanSupplier anObject) {

      if (anObject.getAsBoolean()) return;

      System.out.println("Hi");
  }
}