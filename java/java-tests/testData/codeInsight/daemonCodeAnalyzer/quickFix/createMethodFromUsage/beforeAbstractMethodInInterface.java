// "Create abstract method 'foo'" "false"
class Usage {
    void usage(Target t) {
        t.<caret>foo();
    }
}

interface Target {
}
