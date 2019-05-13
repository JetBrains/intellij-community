public class Test {

    int rMethod() {}

    int foo(int rParam) {
        Object rLocal;
        r<caret> rMethod();
    }

}