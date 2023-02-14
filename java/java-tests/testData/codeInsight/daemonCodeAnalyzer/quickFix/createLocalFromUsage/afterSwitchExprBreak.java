// "Create local variable 'foo'" "true-preview"
class Foo {
    String test(int i) {
        return switch (i) {
            default -> {
                String foo;
                yield foo;
            }
        };
    }
}