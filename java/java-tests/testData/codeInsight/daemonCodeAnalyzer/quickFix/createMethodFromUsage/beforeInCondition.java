// "Create method 'test'" "true-preview"
public class Test {
    void test(boolean b, Object obj) {
        if (b || te<caret>st() == obj) {
            // do smth
        }
    }
}
