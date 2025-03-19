// "Bind method parameters to fields" "true-preview"

class Bar {

    private Class<?> myA;
    private int myB;

    <T> void get(Class<T> a, int b) {
        myA = a;
        myB = b;
    }
}