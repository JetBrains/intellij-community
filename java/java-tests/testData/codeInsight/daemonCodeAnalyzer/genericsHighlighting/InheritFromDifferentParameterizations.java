
interface A<T> {}

interface X1<T> extends A<String> {}
interface Y1<T> extends A<Integer> {}
interface Z1 extends X1, Y1 { }

interface X2 extends A<String> {}
interface Y2<T> extends A<Integer> {}
<error descr="'A' cannot be inherited with different type arguments: 'java.lang.String' and 'null'">interface Z2 extends X2, Y2</error> { }

interface X3 extends A<String> {}
interface Y3 extends A<Integer> {}
<error descr="'A' cannot be inherited with different type arguments: 'java.lang.String' and 'java.lang.Integer'">interface Z3 extends X3, Y3</error> { }