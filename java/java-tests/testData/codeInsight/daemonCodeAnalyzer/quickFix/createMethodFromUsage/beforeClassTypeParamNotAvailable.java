// "Create method 'makeOp'" "true-preview"
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
        super(null, mak<caret>eOp(mop));
    }

   

}