// "Remove variable 'problematic'" "true"
class C {
    Object foo = null;

    void case01() {
        Object <caret>problematic;
        for(int i = 10; (--i) > 0; problematic = foo) {
            System.out.println("index = " + i);
        }
    }
}
