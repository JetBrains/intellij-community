// "Remove variable 'problematic'" "true"
class C {
    Object foo = null;

    void case02() {
        Object <caret>problematic;
        for(int i = 10; i > 0; i--, problematic = foo) {
            System.out.println("index = " + i);
        }
    }
}
