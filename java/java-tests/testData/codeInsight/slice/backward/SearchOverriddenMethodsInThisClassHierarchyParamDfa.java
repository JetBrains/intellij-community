interface I {
    String getValue();
}

interface J extends I {
    
}

class X implements I {
    public String getValue() {return "X";}
}

class Y implements J {
    public String getValue() {return <flown11>"Y";}
}

class Test {
    void foo(I i) {
        if (!(i instanceof J)) return;
        String <caret>s = <flown1>i.getValue();
    }
}