// "Replace lambda with method reference" "true"
import java.util.Random;
import java.util.function.Function;
class Bar extends Random {
    Function<Integer , Integer>  s = (i) -> ne<caret>xt(i);
}