public class Foo {
    void test(int i) {
        while (i-- > 1) {
            i <caret>= 1;
        }
        
    }
}