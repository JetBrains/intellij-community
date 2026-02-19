public class MultipleDeclarations {
    void test() {
        Integer[]  <caret>values1    @Required[]   = new Integer[1][2], values2 @Required @Preliminary [] = new Integer[][]{{3, 3, 3}, {3, 3, 3}},  values3[] = new Integer[5][6], values4 @Preliminary   [];
    }
}