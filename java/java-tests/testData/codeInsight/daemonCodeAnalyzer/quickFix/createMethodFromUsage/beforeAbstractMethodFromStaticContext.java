// "Create abstract method 'foo'" "false"
class Usage {
    void usage(Target t) {
        Target.<caret>foo();
    }
}

abstract class Target {
}
