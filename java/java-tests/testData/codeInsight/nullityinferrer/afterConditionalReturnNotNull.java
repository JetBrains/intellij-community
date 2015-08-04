import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
    @NotNull
    public  String noNull(@Nullable String text) {
        return text == null ? "" : text;
    }
}