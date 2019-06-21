// "Create local variable 'foo'" "false"
class Foo {
    int x = foo ? 0 : switch(1) {
        default -> f<caret>oo;
    };
}