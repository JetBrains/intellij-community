// "Bind constructor parameters to fields" "true-preview"

class A{
    private final String myManager;
    private final Integer myNewManager;

    public A(String oldManager, Integer newManager) {
        myManager = oldManager;
        myNewManager = newManager;
    }
}

