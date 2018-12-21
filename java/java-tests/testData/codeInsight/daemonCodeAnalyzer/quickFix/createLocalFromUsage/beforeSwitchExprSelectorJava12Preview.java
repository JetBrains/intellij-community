// "Create local variable 'foo'" "true"
class Foo {
    String test(int i) {
        return switch (f<caret>oo) {
            default -> {
                break i;
            }
        };
    }
}