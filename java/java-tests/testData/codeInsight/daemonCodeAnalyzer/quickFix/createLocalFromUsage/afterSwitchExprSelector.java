// "Create local variable 'foo'" "true-preview"
class Foo {
    String test(int i) {
        int foo;
        return switch (foo) {
            default -> {
                yield i;
            }
        };
    }
}