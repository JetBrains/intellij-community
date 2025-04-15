// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
import org.jetbrains.annotations.NotNull;

class SomeClass {
    record Problem(int myLine, int myColumn, @NotNull String code) {

        static Problem make() {
            int lineArg = 0;
            int columnArg = 42;
            return new Problem(lineArg, columnArg, "");
        }
    }
}
