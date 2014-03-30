interface A
{
    abstract String foo();
}

interface B
{
    abstract Object foo();
}

class C implements A, B
{
    public String foo() {
        <selection>return null;</selection>
    }
}