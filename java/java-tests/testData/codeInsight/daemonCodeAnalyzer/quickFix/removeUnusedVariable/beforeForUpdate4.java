// "Remove variable 'problematic'" "true"
class C {
    Object foo() {return null;}

    void case01() {
        Object <caret>problematic;
        for(int i = 10; i > 0; --i, problematic = foo()) {
            System.out.println("index = " + i);
        }
    }
}
