public class SomeClass {

    public void foo(int count) {}
    public static Object foo(int count, String descr) {

    }

}

class SomeClassImpl {
    public Object foo(int count, String description) {
        SomeClass.foo(<caret>);
    }
}
