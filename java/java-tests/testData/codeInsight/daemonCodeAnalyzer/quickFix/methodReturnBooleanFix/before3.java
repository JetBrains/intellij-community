// "Make 'victim()' return 'boolean'" "true-preview"
class Test {
    public void trigger() {
        if (<caret>victim(false, 0, false)) {
            System.out.println("***");
        }
    }

    public void victim(final boolean param1, final int param2, final boolean param3) {
        final int something = 1;
        int another = 10;
        boolean someValue = true;
    }
}