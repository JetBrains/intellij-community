// "Remove local variable 'problematic'" "true-preview"
class C {
    native int foo();

    void case01() {
        Object <caret>problematic;
        for(int i = 10; i > 0; problematic = foo() + foo()) {
            System.out.println("index = " + i);
        }
    }
}
