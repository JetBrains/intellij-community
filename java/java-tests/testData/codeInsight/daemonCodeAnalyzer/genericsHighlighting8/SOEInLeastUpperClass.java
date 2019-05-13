<error descr="Cyclic inheritance involving 'A'">interface A extends A</error> {}
interface B {}

<error descr="Cyclic inheritance involving 'A'">class T implements A, B</error>{
    <T1> T1 foo(A a, B b) {
        return null;
    }

    void bar (boolean a, A a1, B b1){
        T t = a ? <error descr="Incompatible types. Found: 'A', required: 'T'">a1</error> : <error descr="Incompatible types. Found: 'B', required: 'T'">b1</error>;
    }
}
