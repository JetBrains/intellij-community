// "Remove switch label 'null'" "true-preview"
class X {
    void test(String s) {
        switch (s) {
            case null -> System.out.println("null");
            case null<caret>, default -> System.out.println("null or default");
        }
    }
}
