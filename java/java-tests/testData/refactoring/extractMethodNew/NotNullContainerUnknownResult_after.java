import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.UnknownNullability;

@NotNullByDefault
class C {
    void test() {

        String s = newMethod();

        System.println(s);
    }

    private @UnknownNullability String newMethod() {
        String s = getString();
        return s;
    }

    @UnknownNullability String getString() {
        return "";
    }
}
