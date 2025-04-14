// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
import org.jetbrains.annotations.NotNull;

class SomeClass {
    public record Problem(int myLine, int myColumn, @NotNull String code) {
    }
}
