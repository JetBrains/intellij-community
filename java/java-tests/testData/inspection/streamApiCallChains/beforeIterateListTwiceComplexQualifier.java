// "Replace IntStream.range().mapToObj() with list.get(...).stream()" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Test extends ArrayList<String> {
    public void test(List<List<String>> list) {
        String[] arr2 = IntStream.range(0, list.get(0).size()) // comment 0
            .m<caret>apToObj(index -> list./*comment1*/get(0).get(index) + /*comment2*/"!!!" + // comment3
                                      list.get(0).get(/*comment4*/index))
            .toArray(String[]::new);
    }
}
