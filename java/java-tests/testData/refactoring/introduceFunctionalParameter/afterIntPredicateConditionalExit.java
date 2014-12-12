import java.util.function.Function;

class Test {
  void bar() {
    foo(1, new Function<Integer,Boolean>() {
        public boolean apply(Integer i) {
            if (i > 0) {
                System.out.println(i);
                System.out.println(i);
                return true;
            }
            return false;
        }
    });
  }

  void foo(int i, Function<Integer, Boolean> anObject) {

      if (anObject.apply(i)) return;

      System.out.println("Hi");
  }
}