class T {
    I myLastI;
    I getLastI() { return myLastI; }

    void setLastI(I a) {
        myLastI = <caret>
    }
}

class I {
    static final I _1 = new I();
    static final I _2a = new I();
    static final I _3a = new I();
    static final I _4a = new I();
    static final I _5a = new I();
    static final I _6a = new I();
    static final I _7a = new I();
    static final I _8a = new I();
    static final I _9a = new I();

    static I valueOf(String sth) {
        return null;
    }
}