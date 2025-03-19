import java.util.function.IntFunction;

public class LocalClass {
    <T> void test(int x, T t) {

        MyObject<Integer, T> h = new MyObject<>(1, x);
        MyObject<String, T> h2 = new MyObject<>("1", x);
        MyObject raw = new MyObject("1", x);
        IntFunction<MyObject<Character, T>> ic = a -> new MyObject<>(a, x);
        System.out.println(new MyObject<Number, T>(1, x) {
            void test() {}
        });
    
        h2.run("hello", t);
    }

    private static class MyObject<X, T> {
        private final int myX;

        MyObject(int a, int x) {
            myX = x;
            System.out.println("hi"+x);
        }
        MyObject(String a, int x) {
            myX = x;
            System.out.println("hi"+x);
        }
  
        static {
            System.out.println("hello");
        }

        void run(X xx, T t) {
            System.out.println(xx);
            System.out.println(myX);
            System.out.println(MyObject.class);
            var xHello = new MyObject<X, T>(3, myX);
            System.out.println(xHello);
            xHello.run(xx, t);
        }
    }
}
