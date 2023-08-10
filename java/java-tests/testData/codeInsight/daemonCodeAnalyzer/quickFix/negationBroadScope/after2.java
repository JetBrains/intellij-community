// "Change to '!(this instanceof Object)'" "true-preview"
public class Foo {
    void task() {
        if (<caret>!(this instanceof Object)) {}
    }
}
