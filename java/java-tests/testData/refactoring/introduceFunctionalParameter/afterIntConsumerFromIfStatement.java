import java.util.function.Consumer;

class Test {
    void bar() {
    foo(1, new Consumer<Integer>() {
        public void accept(Integer i) {
            System.out.println(i);
            System.out.println(i);
        }
    });
  }
  
  void foo(int i, Consumer<Integer> anObject) {
    if (i > 0) {

        anObject.accept(i);

    }
  }
}