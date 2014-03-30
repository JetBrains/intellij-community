class A {
    void f(String p){
        String <caret>t;
        if (p == null)
            t = "1";
        System.out.println("");
    }
}