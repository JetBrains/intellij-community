// "Remove local variable 'problematic'" "true-preview"
class C {
    Object foo() {return null;}

    void case01() {
        for(int i = 10; i > 0; --i, foo()) {
            System.out.println("index = " + i);
        }
    }
}
