import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnknownNullability;

@NotNullByDefault
class C {
    void test() {
        <selection>
        String s = getString();
        </selection>
        System.println(s);
    }

    @UnknownNullability String getString() {
        return "";
    }
}
