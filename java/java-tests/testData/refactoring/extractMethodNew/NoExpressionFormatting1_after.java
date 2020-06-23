import org.jetbrains.annotations.NotNull;

class C {
    void foo(Object o) {
        newMethod().run();
    }

    @NotNull
    private Runnable newMethod() {
        return (Runnable)(() -> System.out.println());
    }
}