import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
class C {
    void test(String s) {

        newMethod(s);

    }

    private void newMethod(String s) {
        System.out.println(s);
    }
}
