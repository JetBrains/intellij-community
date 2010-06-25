// "Make 'victim' return 'boolean'" "true"
class Test {
    public void trigger() {
        if (<caret>victim()) {
            System.out.println("***");
        }
    }

    public void victim() {
        final int something = 1;
        int another = 10;
    }
}