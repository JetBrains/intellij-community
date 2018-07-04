// "Create field 'foo'" "false"
class Usage {
    void usage(A a) {
        a.<caret>foo()
    }
}

class A {
}
