
import java.io.Serializable;
import java.util.List;

class MyTest {
    static <T extends Object & Serializable, J> void m1(List<J> other) {
       List<T> list = (List<T>) other;
    }
    static <T extends Object & Serializable> void m2(List<Object> other) {
       List<T> list = <error descr="Inconvertible types; cannot cast 'java.util.List<java.lang.Object>' to 'java.util.List<T>'">(List<T>) other</error>;
    }
    static <T extends Serializable> void m3(List<Object> other) {
       List<T> list = <error descr="Inconvertible types; cannot cast 'java.util.List<java.lang.Object>' to 'java.util.List<T>'">(List<T>) other</error>;
    }
}
