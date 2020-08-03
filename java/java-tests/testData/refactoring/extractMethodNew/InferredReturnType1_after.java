import org.jetbrains.annotations.NotNull;

class Test {
    public Object test(boolean b) {
        return newMethod();
    }

    @NotNull
    private String newMethod() {
        return "42";
    }
}