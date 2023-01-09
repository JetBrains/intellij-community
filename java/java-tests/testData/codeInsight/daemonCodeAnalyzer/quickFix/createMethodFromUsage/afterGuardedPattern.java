// "Create method 'isEmpty' in 'Main'" "true-preview"
class Main {
    void foo(Object obj) {
        switch (obj) {
            case String s && isEmpty(s) -> {}
            default -> {}
        }
    }

    private boolean isEmpty(String s) {
        return false;
    }
}