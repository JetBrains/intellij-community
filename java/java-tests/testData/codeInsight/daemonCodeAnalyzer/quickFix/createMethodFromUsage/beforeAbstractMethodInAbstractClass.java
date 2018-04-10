// "Create abstract method 'foo'" "true"
class Usage {
    void usage(Target t) {
        t.<caret>foo();
    }
}

abstract class Target {
}
