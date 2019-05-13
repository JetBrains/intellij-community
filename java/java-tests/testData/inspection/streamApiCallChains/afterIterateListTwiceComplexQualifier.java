// "Replace IntStream.range().mapToObj() with list.get(...).stream()" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Test extends ArrayList<String> {
    public void test(List<List<String>> list) {
        /*comment1*/
        /*comment4*/
        // comment 0
        String[] arr2 = list.get(0).stream().map(s -> s + /*comment2*/"!!!" + // comment3
                s)
            .toArray(String[]::new);
    }
}
