// "Remove switch label 'null'" "true-preview"
class X {
    void test(String s) {
        switch (s) {
            case null -> System.out.println("null");
            default -> System.out.println("null or default");
        }
    }
}
