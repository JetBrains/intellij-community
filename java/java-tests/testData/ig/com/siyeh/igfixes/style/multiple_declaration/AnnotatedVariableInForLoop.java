public class MultipleDeclarations {
    void test() {
        for (int <caret>i = 0, arr @Required [] @Required []  = new int[2][1]; i < array.length; i++) {
        }
    }
}