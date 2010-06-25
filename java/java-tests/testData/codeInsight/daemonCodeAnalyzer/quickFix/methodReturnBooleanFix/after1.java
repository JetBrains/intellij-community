// "Make 'victim' return 'boolean'" "true"
class Test {
    public void trigger() {
        if (victim()) {
            System.out.println("***");
        }
    }

    public boolean victim() {
        final int something = 1;
        final boolean value = false;
        int another = 10;
        final boolean secondBoolean = another == something;
        return <caret><selection>value</selection>;
    }
}