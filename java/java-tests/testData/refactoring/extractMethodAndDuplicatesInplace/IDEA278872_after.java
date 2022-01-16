import org.jetbrains.annotations.NotNull;

class Test {
    void test() {
        final String str = getString();

        System.out.println(str);
    }

    @NotNull
    private String getString() {
        final String str = "atata";
        do {
          System.out.println();
        } while (Math.random() > 0.5);
        return str;
    }
}