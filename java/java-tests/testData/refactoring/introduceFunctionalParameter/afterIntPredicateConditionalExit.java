import java.util.function.IntPredicate;

class Test {
  void bar() {
    foo(1, new IntPredicate() {
        public boolean test(int i) {
            if (i > 0) {
                System.out.println(i);
                System.out.println(i);
                return true;
            }
            return false;
        }
    });
  }

  void foo(int i, IntPredicate anObject) {

      if (anObject.test(i)) return;

      System.out.println("Hi");
  }
}