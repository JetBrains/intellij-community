import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
class C {
    void test() {
        <selection>
        String s = getString();
        </selection>
        System.println(s);
    }

    String getString() {
        return "";
    }
}
