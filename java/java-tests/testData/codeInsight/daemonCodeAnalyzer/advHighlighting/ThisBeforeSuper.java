class A
{
    class B
    {
    }
}


class C extends A
{
    class D extends B
    {
        D(){
            C.this.super();
        }
    }
}

class C1 {
    class B1 {}
    class D1 extends C1.B1 {
        D1() {
            C1.this.super();
        }
    }
}