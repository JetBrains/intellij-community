class C<<warning descr="Type parameter 'T' is never used">T</warning>>{}
class A<<warning descr="Type parameter 'S' is never used">S</warning>,<warning descr="Type parameter 'T' is never used">T</warning>> {}
class B<S,T extends C<S>> extends A<S,T> {
    void foo(B<?,?> x){
        bar(x);
    }
    <S, T> void bar(A<S,T> x){
        System.out.println(x);
    }
}