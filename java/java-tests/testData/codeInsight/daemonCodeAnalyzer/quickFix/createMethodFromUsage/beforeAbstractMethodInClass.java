// "Create abstract method 'foo'" "false"
class Usage {
    void usage(Target t) {
        t.<caret>foo();
    }
}

class Target {
}
