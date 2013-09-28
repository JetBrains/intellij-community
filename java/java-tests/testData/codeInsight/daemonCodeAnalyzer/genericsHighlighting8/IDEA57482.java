class C<T>{
    static class D{
        class E{
            <error descr="'C.this' cannot be referenced from a static context">T</error> x;
        }
    }
}

class T{}
