import java.util.function.Function;

class Test {
    {
        double y = 2.0;
        int x = 1;
        Function<Integer, Integer> integerIntegerFunction = x1 -> x1++;
        double z = integerIntegerFunction.apply(x) + y;
    
        System.out.println(z);
    }
}