// "Create local variable 'foo'" "true-preview"
class Foo {
    int x = foo ? 0 : switch(1) {
        default -> {
            yield f<caret>oo;
        }
    };
}