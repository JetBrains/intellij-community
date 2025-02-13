import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

class MyTest {

    public List<Integer> someMethod() {
        <error descr="Cannot infer type: 'var' on variable without an initializer">var</error> listOfInteger;
        Integer[] arrayOfInteger = {2, 4, 8};
        listOfInteger = Arrays.stream(arrayOfInteger)
                .filter(number -> number >= 4)
                .collect(Collectors.toCollection(ArrayList::new));
        return listOfInteger;
    }
}
