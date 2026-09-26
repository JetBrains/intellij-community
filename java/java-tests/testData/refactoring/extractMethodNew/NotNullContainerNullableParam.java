import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
class C {
    public void test(@Nullable String s) {
        <selection>
        System.out.println((s == null ? "" : s).trim());
        </selection>
    }
}
