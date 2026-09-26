// "Generate overloaded constructor with default parameter values" "true"
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Rec(@NotNull String a, @Nullable String b, boolean c) {
    public Rec() {
        this(null, null, false);
    }
}
