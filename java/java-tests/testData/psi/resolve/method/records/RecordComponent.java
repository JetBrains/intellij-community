public class Test1 {
    record A(int value) {}

    void test(A a){
        a.<caret>value();
    }
}
