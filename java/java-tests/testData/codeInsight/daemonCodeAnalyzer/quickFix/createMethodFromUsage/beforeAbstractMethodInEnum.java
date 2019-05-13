// "Create abstract method 'foo'" "false"
class Usage {
    void usage(Target t) {
        t.<caret>foo();
    }
}

enum Target {
    ;
}
