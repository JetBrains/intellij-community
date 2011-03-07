interface IA{
    void fooa();
}

interface IB{
    void foob();
}

interface IC<T extends IA>{
    T c();
}

class K {
    void foo(IC<? extends IB> x){
        x.c().fo<caret>

    }
}