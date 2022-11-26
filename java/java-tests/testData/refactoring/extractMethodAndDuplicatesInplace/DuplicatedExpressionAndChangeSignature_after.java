import org.jetbrains.annotations.NotNull;

class Test {
    public void test() {
        String start1 = getSubstring("one");
        String start2 = getSubstring("two");
    }

    @NotNull
    private static String getSubstring(String one) {
        return one.substring(0, 10);
    }
}