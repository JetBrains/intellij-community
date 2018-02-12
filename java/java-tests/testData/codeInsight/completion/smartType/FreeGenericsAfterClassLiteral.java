public class Foo  {

    void foo(Object o) {
        String s = Util.tryCast(o, <caret>);
    }

}

class Util {
    static <T> T tryCast(Object obj, Class<T> clazz) {}
}