// "Create local variable 'inner'" "true"
class OuterGeneric<T> {
    class Inner {
    }
    static OuterGeneric<Object>.Inner create() {
        return null;
    }
    public static void main(String[] args) {
        i<caret>nner = create();
    }
}