import org.jetbrains.annotations.NotNull;

class Test {
    public Object test(boolean b) {
        return newMethod();
    }

    private @NotNull String newMethod() {
        return "42";
    }
}