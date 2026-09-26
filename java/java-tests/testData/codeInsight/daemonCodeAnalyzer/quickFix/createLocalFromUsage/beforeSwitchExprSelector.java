// "Create local variable 'foo'" "true-preview"
class Foo {
    String test(int i) {
        return switch (f<caret>oo) {
            default -> {
                yield i;
            }
        };
    }
}