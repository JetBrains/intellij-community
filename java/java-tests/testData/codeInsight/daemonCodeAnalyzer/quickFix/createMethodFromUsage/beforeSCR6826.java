// "Create method 'f'" "true-preview"
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
