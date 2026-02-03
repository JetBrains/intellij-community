import java.util.function.IntFunction;

public class LocalClass {
    <T> void test(int x, T t) {

        Hello<T> h = new Hello<>(1, x);
        IntFunction<Hello<T>> ic = a -> new Hello<>(a, x);
        System.out.println(new Hello<T>(1, x) {
            void test() {}
        });
    
        h.run(t);
    }

    private static class Hello<T> {
        private final int x;

        Hello(int a, int x) {
            this.x = x;
            System.out.println("hi"+x);
        }
        Hello(String a, int x) {
            this.x = x;
            System.out.println("hi"+x);
        }
  
        static {
            System.out.println("hello");
        }

        void run(T t) {
            System.out.println(x);
            System.out.println(Hello.class);
            var xHello = new Hello<T>(3, x);
            System.out.println(xHello);
            xHello.run(t);
        }
    }
}
