import org.jetbrains.annotations.NotNull;

class SomeClass {

    public void test(String filePath) {
        var s = getS();
        System.out.println(s);
    }

    private static @NotNull String getS() {
        var s = "42";
        return s;
    }
}