import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
class C {
    void test() {

        String s = newMethod();

        System.println(s);
    }

    private @Nullable String newMethod() {
        String s = getString();
        return s;
    }

    @Nullable String getString() {
        return "";
    }
}
