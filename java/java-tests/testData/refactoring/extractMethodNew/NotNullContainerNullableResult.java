import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
class C {
    void test() {
        <selection>
        String s = getString();
        </selection>
        System.println(s);
    }

    @Nullable String getString() {
        return "";
    }
}
