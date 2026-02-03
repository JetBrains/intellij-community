class Utilz {
    static int func(){
        return 1;
    }

    static int foo1(){
        return 2*Utilz.func();
    }

    static int fo<caret>o2(){
        return 2*func();
    }

}

class Something {
    Something(){
        int x = 2*Utilz.func();
    }
}