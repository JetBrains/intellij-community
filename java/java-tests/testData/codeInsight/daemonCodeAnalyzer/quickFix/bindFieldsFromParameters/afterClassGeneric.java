// "Bind method parameters to fields" "true-preview"

class Bar<T extends Number> {

    private Class<T> myA;
    private int myB;

    void get(Class<T> a, int b) {
        myA = a;
        myB = b;
    }
}