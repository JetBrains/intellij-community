// "Bind constructor parameters to fields" "true"

class A{
    private final String myOldClass;
    private final String myNewClass;

    public A(String oldClass, String newClass) {
        myOldClass = oldClass;
        myNewClass = newClass;
    }
}

