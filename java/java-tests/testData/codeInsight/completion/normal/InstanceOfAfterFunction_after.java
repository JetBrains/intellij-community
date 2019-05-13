import java.math.BigInteger;
import java.util.function.Function;

class Test {
    private static boolean instanceOfAfterFunction(Integer lambda,
                                                   Function<Integer, Number> replacer, Object type) {
        Number function = replacer.apply(lambda);
        if (function instanceof BigInteger && ((BigInteger)function).bitCount() > 0) {
            return false;
        }
        if (type instanceof String && ((String) type).substring()) {
            return false;
        }
        return true;
    }

}