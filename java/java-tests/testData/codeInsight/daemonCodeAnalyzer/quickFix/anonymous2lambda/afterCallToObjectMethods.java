// "Fix all 'Anonymous type can be replaced with lambda' problems in file" "true"
import java.util.concurrent.Callable;
class MyTest {

    interface A {
        void foo();
        default void m() {}
    }

    static {
        Callable<Integer> c = new Callable<Integer>() {
            @Override
            public Integer call() {
                return hashCode();
            }
        };
        A a = new A() {
            @Override
            public void foo() {
                 m();
            }
        };
        A b = () -> new Object();
    }
}
