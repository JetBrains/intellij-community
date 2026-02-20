// "Generate overloaded constructor with default parameter values" "true"
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record <caret>Rec(@NotNull String a, @Nullable String b, boolean c) {
}
