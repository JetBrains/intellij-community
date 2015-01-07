// "Create method 'makeOp'" "true"
class Base<T> {
    Base(Factory<T> factory, Operator<T> operator) {
    }
}

interface MetaOperator<T> {
}

interface Operator<T> {
}

interface Factory<T> {
}

class Sup<T> extends Base<T> {
    Sup(MetaOperator<T> mop) {
        super(null, makeOp(mop));
    }

    private static <T> Operator<T> makeOp(MetaOperator<T> mop) {
        <selection>return null;</selection>
    }


}