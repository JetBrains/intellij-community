public class Test {
    void anotherMethod(String s);
    String field;
    /**
     * @param anObject
     * @param field
     */
    static void method(Test anObject, String field) {
        anObject.anotherMethod(field);
    }
}