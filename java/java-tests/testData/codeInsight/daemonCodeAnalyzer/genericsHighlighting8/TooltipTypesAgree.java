class MyTest {
    void subject(Generic<? extends Number, Number, Integer> pSuper,
                        Generic<Integer, Integer, Integer> pSub) {
        <error descr="Incompatible types. Found: 'Generic<java.lang.Integer,java.lang.Integer,java.lang.Integer>', required: 'Generic<? extends java.lang.Number,java.lang.Number,java.lang.Integer>'">pSuper =  pSub</error>;
    }
}
class Generic<U, V, W> {} 