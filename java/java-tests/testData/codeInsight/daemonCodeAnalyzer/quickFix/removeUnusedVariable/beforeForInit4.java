// "Remove variable 'problematic'" "true"
class C {
    Object foo() {return null;}

    void case01() {
        Object <caret>problematic;
        int i;
        for(i = 10, problematic = foo(); (--i) > 0; ) {
            System.out.println("index = " + i);
        }
    }
}
