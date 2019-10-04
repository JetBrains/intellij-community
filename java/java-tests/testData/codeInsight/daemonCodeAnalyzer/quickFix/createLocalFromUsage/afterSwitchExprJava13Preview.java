// "Create local variable 'foo'" "true"
class Foo {
    String test(int i) {
        String foo;
        return switch (i) {
            default -> foo;
        };
    }
}