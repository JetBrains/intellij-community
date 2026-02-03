// "Create constructor matching super" "true"
public class Test<T> {
    Test (T t) {}
}

class Derived extends Test<String> {
    <caret>Derived(String s) {
        super(s);
    }
}