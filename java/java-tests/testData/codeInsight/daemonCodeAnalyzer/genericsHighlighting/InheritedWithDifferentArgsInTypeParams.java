interface IA<T> {}
interface IB<T> extends IA<T> {}

class A {
    <<error descr="'IA' cannot be inherited with different type arguments: 'java.lang.Integer' and 'java.lang.String'"></error>T extends IA<Integer> & IB<String>> void foo(){}
}



interface IA1<T> {}
interface IB1<T> extends IA1<T> {}

class A1 {
    <<error descr="'IA1' cannot be inherited with different type arguments: 'java.lang.Object' and 'capture<?>'"></error>T extends IA1<Object> & IB1<?>> void foo(){}
}

interface IA2<T> {}
interface IB2<T> extends IA2<T[]> {}

class A2 {
    <T extends IA2<Object[]> & IB2<Object>> void foo(){}
}


