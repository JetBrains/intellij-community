// "Create method 'f'" "true"
class A {
    {
        new Runnable() {
            public void run() {
                B.f(this);
            }
        };
    }
}
class B {
    public static void f(Runnable runnable) {
        <caret><selection></selection>
    }
}
