import org.jetbrains.annotations.NotNull;

class Test {

    void foo(Object x) {
        if (x instanceof String) x = newMethod((String) x);
        if (x instanceof String) x = newMethod((String) x);
    }

    @NotNull
    private String newMethod(String x) {
        return x.substring(1);
    }
}