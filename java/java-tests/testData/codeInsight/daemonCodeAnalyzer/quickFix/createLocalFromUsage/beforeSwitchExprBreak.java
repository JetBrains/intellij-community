// "Create local variable 'foo'" "true-preview"
class Foo {
    String test(int i) {
        return switch (i) {
            default -> {
                yield fo<caret>o;
            }
        };
    }
}