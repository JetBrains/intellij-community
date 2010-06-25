// "Create Method 'f'" "true"
class A {
    {
        new Runnable() {
            public void run() {
                B.<caret>f(this);
            }
        };
    }
}
class B {
}
