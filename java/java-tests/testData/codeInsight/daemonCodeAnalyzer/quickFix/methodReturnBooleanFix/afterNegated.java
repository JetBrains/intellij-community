// "Make 'victim()' return 'boolean'" "true-preview"
class Test {
    public void trigger() {
        if (!victim()) {
            System.out.println("***");
        }
    }

    public boolean victim() {
        final int something = 1;
        final boolean value = false;
        int another = 10;
        final boolean secondBoolean = another == something;
        return value;
    }
}