// "Create Method 'f'" "true"
class A {
    {
         f(() -> {});
    }

    private void f(Object p0) {
        <caret><selection></selection>
    }
}
