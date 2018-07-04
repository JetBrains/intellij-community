// "Remove variable 'problematic'" "true"
class C {
    Object foo() {return null;}

    void case01() {
        int i;
        for(i = 10, foo(); (--i) > 0; ) {
            System.out.println("index = " + i);
        }
    }
}
