// "Remove local variable 'problematic'" "true-preview"
class C {
    Object foo = null;

    void case01() {
        int i = 10;
        for(; (--i) > 0; ) {
            System.out.println("index = " + i);
        }
    }
}
