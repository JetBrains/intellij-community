public class A {
    private final String myData;

    public A(String someData) {
        myData = someData;
    }

    void test(String <caret>data) {
        System.out.println(data);
    }

    void callTest() {
        test(myData);        
    }

    void alsoCallTest() {
        test(myData);
    }
}