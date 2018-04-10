import org.jetbrains.annotations.Nullable;

class C {
    private @Nullable String s;

    public C(@Nullable String s) {
        this.s = s;
    }
}