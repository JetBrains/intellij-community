// "Create local variable 'foo'" "true-preview"
class Foo {
    String test(int i) {
        String foo;
        return switch (i) {
            default -> foo;
        };
    }
}