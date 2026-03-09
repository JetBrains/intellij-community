import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
class C {
    public void test(@Nullable String s) {

        newMethod(s);

    }

    private void newMethod(@Nullable String s) {
        System.out.println((s == null ? "" : s).trim());
    }
}
