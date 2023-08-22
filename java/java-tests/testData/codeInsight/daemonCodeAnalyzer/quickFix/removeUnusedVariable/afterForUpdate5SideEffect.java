// "Remove local variable 'problematic'" "true-preview"
class C {
    native int foo();

    void case01() {
        for(int i = 10; i > 0; --i, foo(), foo()) {
            System.out.println("index = " + i);
        }
    }
}
