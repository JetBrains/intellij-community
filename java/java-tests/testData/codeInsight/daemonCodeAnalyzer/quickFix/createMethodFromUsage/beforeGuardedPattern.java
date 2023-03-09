// "Create method 'isEmpty' in 'Main'" "true-preview"
class Main {
    void foo(Object obj) {
        switch (obj) {
            case String s && isEmpt<caret>y(s) -> {}
            default -> {}
        }
    }
}