class Main implements I {

    public Main(int i) { }

    {
        new Main(I.super.m()) {};
    }

}

interface I {
    default int m(){
        return 42;
    }
}

class Main1 extends I1 {

    public Main1(int i) {
    }

    {
        new Main1(<error descr="'I1' is not an enclosing class">I1.super</error>.m()) {};
    }

}

abstract class I1 {
    public int m(){
        return 42;
    }
}
