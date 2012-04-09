package pck;

interface IA<T> {
    T a();
}

interface IB {
    String a();
}

abstract class C implements IA<String>, IB {

    {
        a();
    }
}


interface IAO {
    Object a();
}

abstract class CO implements IAO, IB {

    {
        a();
    }
}