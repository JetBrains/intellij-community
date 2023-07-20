import org.jetbrains.annotations.NotNull;

class SomeClass {

    public void test(String filePath) {
        var s = getString();
        System.out.println(s);
    }

    @NotNull
    private static String getString() {
        String s = "42";
        return s;
    }
}