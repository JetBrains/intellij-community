// "Create abstract method 'foo'" "true-preview"
class Usage {
    void usage(Target t) {
        t.foo();
    }
}

enum Target {
    ;

    abstract void bar();

    public abstract void foo();
}
