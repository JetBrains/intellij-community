import java.util.function.IntConsumer;

class Test {
    void bar() {
    foo(1, new java.util.function.IntConsumer() {
        public void accept(int i) {
            System.out.println(i);
            System.out.println(i);
        }
    });
  }
  
  void foo(int i, IntConsumer anObject){

      anObject.accept(i);

  }
}