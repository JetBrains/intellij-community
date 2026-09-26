import java.util.function.IntFunction;

public class LocalClass {
    <T> void test(int x, T t) {
        class Hell<caret>o {
            Hello(int a) {}
            Hello(String a) {}
      
            static {
                System.out.println("hello");
            }
      
            {
                System.out.println("hi"+x);
            }
      
            void run(T t) {
                System.out.println(x);
                System.out.println(Hello.class);
                var xHello = new Hello(3);
                System.out.println(xHello);
                xHello.run(t);
            }
        }
    
        Hello h = new Hello(1);
        IntFunction<Hello> ic = Hello::new;
        System.out.println(new Hello(1) {
            void test() {}
        });
    
        h.run(t);
    }
}
