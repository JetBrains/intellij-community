
import java.util.Comparator;

class InlineLambda {
    private static int compare(Integer i, Integer j) {
        if (i > j) {
            return 1;
        }
        if (j > i) {
            return -1;
        }
        return 0;
    }

    private static Comparator<Integer> COMP = (o1, o2) -> com<caret>pare(o1, o2);
}