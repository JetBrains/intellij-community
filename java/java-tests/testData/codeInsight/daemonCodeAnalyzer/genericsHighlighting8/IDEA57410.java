
interface IA {
    <T> void a(Iterable<String> x);
}

interface IB {
    <T> void a(Iterable x);
}

<error descr="'a(Iterable)' in 'IB' clashes with 'a(Iterable<String>)' in 'IA'; both methods have same erasure, yet neither overrides the other">abstract class C implements IA, IB</error> {}
