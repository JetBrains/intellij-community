// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
import org.jetbrains.annotations.NotNull;

class SomeClass {
    record Problem(int myColumn, int myLine, @NotNull String code) {
        // Notice: order of parameters differs from order of instance fields 

        static Problem make() {
            int columnArg = 42;
            int lineArg = 0;
            return new Problem(columnArg, lineArg, "");
        }
    }
}
