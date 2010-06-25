// "Make 'victim' return 'boolean'" "true"
class Test {
    public void trigger() {
        if (victim()) {
            System.out.println("***");
        }
    }

    public boolean victim() {
        final int something = 1;
        int another = 10;
        return <caret><selection>false</selection>;
    }
}