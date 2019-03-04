// "Create local variable 'foo'" "true"
class Foo {
    String test(int i) {
        int foo;
        return switch (foo) {
            default -> {
                break i;
            }
        };
    }
}