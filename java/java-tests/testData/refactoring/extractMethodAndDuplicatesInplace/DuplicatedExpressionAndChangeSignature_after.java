import org.jetbrains.annotations.NotNull;

class Test {
    public void test() {
        String start1 = getSubstring("one");
        String start2 = getSubstring("two");
    }

    private static @NotNull String getSubstring(String one) {
        return one.substring(0, 10);
    }
}