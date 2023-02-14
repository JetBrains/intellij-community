// "Create abstract method 'foo'" "true-preview"
class Usage {
    void usage(Target t) {
        t.foo();
    }
}

abstract class Target {
    public abstract void foo();
}
