// "Create abstract method 'foo'" "true"
class Usage {
    void usage(Target t) {
        t.<caret>foo();
    }
}

enum Target {
    ;

    abstract void bar();
}
