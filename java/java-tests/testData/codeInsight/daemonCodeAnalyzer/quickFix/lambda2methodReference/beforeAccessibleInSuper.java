// "Replace lambda with method reference" "false"
import java.util.Random;
import java.util.function.Function;
class Bar extends Random {
    Function<Integer , Integer>  s = (i) -> super.ne<caret>xt(i);
}