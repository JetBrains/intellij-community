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
        <selection>return null;  //To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}