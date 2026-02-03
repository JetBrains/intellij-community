import org.jetbrains.annotations.NotNull;

class Test {
    void foo() {
        bar(newMethod());
        baz(newMethod());
    }

    @NotNull
    private String newMethod() {
        return String.valueOf(1);
    }

    private void bar(String s) {
    }

    private void baz(String... s) {
    }
}