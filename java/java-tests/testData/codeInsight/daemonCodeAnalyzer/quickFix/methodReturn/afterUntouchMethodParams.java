// "Make 'f' return 'int'" "true"
interface SomeInterface<T> {
    int f(T t);
}

class B implements SomeInterface<String> {
    @Override
    public int f(String s) {
        return 1;
    }
}