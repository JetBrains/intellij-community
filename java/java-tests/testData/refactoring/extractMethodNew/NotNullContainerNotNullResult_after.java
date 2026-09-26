import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
class C {
    void test() {

        String s = newMethod();

        System.println(s);
    }

    private String newMethod() {
        String s = getString();
        return s;
    }

    String getString() {
        return "";
    }
}
