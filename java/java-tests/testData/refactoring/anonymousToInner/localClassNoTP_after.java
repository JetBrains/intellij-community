import java.util.function.IntFunction;

public class LocalClass {
    <T> void test(int x, T t) {

        InnerClass<T> h = new InnerClass<>(1, x);
        IntFunction<InnerClass<T>> ic = a -> new InnerClass<>(a, x);
        System.out.println(new InnerClass<T>(1, x) {
            void test() {}
        });
    
        h.run(t);
    }

    private static class InnerClass<T> {
        private final int x;

        InnerClass(int a, int x) {
            this.x = x;
            System.out.println("hi"+x);
        }
        InnerClass(String a, int x) {
            this.x = x;
            System.out.println("hi"+x);
        }
  
        static {
            System.out.println("hello");
        }

        void run(T t) {
            System.out.println(x);
            System.out.println(InnerClass.class);
            var xHello = new InnerClass<T>(3, x);
            System.out.println(xHello);
            xHello.run(t);
        }
    }
}
