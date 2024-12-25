// "Bind method parameters to fields" "true-preview"

class Bar {

    private int myB;

    <T> void get(Class<T> a, int b) {
        myB = b;
    }
}