// "Make 'f' return 'int'" "true"
interface SomeInterface<T> {
    void f(T t);
}

class B implements SomeInterface<String> {
    @Override
    public void f(String s) {
        return <caret>1;
    }
}