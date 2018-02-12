// "Remove variable 'problematic'" "true"
class C {
    Object foo() {return null;}

    void case01() {
        int i = 10;
        for(foo(); (--i) > 0; ) {
            System.out.println("index = " + i);
        }
    }
}
