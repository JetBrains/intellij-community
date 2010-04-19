public class Test {
    void anotherMethod(String s);
    String field;
    /**
     * @param anObject
     */
    void method(Test anObject, String field1) {
        anObject.anotherMethod(field1);
    }
}