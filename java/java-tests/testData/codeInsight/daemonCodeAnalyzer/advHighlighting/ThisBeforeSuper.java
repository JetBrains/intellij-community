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