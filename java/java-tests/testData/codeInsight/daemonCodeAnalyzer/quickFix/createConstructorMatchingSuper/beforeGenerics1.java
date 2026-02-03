// "Create constructor matching super" "true"
public class Test<T> {
    Test (T t) {}
}

class <caret>Derived extends Test<String> {
}