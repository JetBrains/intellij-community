// "Create Method 'f'" "true"
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
        <selection>//To change body of created methods use File | Settings | File Templates.<caret></selection>
    }
}
