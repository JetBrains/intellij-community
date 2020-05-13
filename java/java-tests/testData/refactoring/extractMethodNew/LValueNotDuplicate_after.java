class UUU {
    int myValue;

    UUU() {
        System.out.println(newMethod());
    }

    private int newMethod() {
        return myValue;
    }

    void init() {
        myValue = 0;
    }
}