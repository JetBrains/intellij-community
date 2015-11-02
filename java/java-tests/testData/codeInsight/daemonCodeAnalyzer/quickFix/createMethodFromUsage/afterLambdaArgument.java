// "Create method 'f'" "true"
class A {
    {
         f(() -> {});
    }

    private void f(Object o) {
        <caret><selection></selection>
    }
}
