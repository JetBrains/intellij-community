// "Create local variable 'foo'" "true"
class Foo {
    String test(int i) {
        return switch (i) {
            default -> fo<caret>o;
        };
    }
}