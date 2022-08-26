// "Make 'victim()' return 'boolean'" "true-preview"
class Test {
    public void trigger() {
        if (<caret>victim()) {
            System.out.println("***");
        }
    }

    public void victim() {
        final int something = 1;
        final boolean value = false;
        int another = 10;
        final boolean secondBoolean = another == something;
    }
}