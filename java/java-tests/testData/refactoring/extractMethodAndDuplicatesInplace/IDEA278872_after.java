import org.jetbrains.annotations.NotNull;

class Test {
    void test() {
        final String str = getStr();

        System.out.println(str);
    }

    private static @NotNull String getStr() {
        final String str = "atata";
        do {
          System.out.println();
        } while (Math.random() > 0.5);
        return str;
    }
}