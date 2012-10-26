class T {
    I lastI;
    I getLastI() { return lastI; }

    void setLastI(I a) {
        lastI = <caret>
    }
}

class I {
    static final I _1 = new I();

    static I valueOf(String sth) {
        return null;
    }
}