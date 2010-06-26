// "Make 'victim' return 'boolean'" "true"
class Test {
    public void trigger() {
        if (victim(false, 0, false)) {
            System.out.println("***");
        }
    }

    public boolean victim(final boolean param1, final int param2, final boolean param3) {
        final int something = 1;
        int another = 10;
        boolean someValue = true;
        return <caret><selection>someValue</selection>;
    }
}