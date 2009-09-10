class Base {
    protected int f;


    public int <caret>getF() {
        return f;
    }
}

class DRV extends Base {
    void f() {
        int f1 = getF();
    }
}
