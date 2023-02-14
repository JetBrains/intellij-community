// "Create method 'f'" "true-preview"
class A {
    {
         f(() -> {});
    }

    private void f(Object o) {
        <caret><selection></selection>
    }
}
