// "Convert to record class" "true-preview"
import org.jetbrains.annotations.NotNull;

class SomeClass {
    public record Problem(int myLine, int myColumn, @NotNull String code) {
    }
}
