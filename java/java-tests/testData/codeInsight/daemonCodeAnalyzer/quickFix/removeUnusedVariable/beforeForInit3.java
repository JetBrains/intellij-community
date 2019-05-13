// "Remove variable 'problematic'" "true"
class C {
    Object foo() {return null;}

    void case01() {
        Object <caret>problematic;
        int i = 10;
        for(problematic = foo(); (--i) > 0; ) {
            System.out.println("index = " + i);
        }
    }
}
