public class CompositeAssignmentCast {
    public static int foo() {
        int <caret>d = 4;
        d *= 1.5;
        return d;
    }
}