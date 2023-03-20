import org.jetbrains.annotations.Nullable;

class C {
    private @Nullable String s;

    C(@Nullable String s) {
        this.s = s;
    }
}