// "Convert to record class" "true-preview"
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
