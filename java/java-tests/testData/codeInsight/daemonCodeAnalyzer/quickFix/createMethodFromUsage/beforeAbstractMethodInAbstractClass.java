// "Create abstract method 'foo'" "true-preview"
class Usage {
    void usage(Target t) {
        t.<caret>foo();
    }
}

abstract class Target {
}
