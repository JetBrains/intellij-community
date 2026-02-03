public class SomeClass {

    public void foo(int count) {}
    public Object foo(int count, String descr) {

    }

}

class SomeClassImpl extends SomeClass {
    public Object foo(int count, String description) {
        super.foo(count, description)<caret>;
    }
}
