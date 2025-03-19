import java.util.function.IntFunction;

public class LocalClass {
    <T> void test(int x, T t) {
        class Hell<caret>o<X> {
            Hello(int a) {}
            Hello(String a) {}
      
            static {
                System.out.println("hello");
            }
      
            {
                System.out.println("hi"+x);
            }
      
            void run(X xx, T t) {
                System.out.println(xx);
                System.out.println(x);
                System.out.println(Hello.class);
                var xHello = new Hello<X>(3);
                System.out.println(xHello);
                xHello.run(xx, t);
            }
        }
    
        Hello<Integer> h = new Hello<Integer>(1);
        Hello<String> h2 = new Hello<>("1");
        Hello raw = new Hello("1");
        IntFunction<Hello<Character>> ic = Hello::new;
        System.out.println(new Hello<Number>(1) {
            void test() {}
        });
    
        h2.run("hello", t);
    }
}
