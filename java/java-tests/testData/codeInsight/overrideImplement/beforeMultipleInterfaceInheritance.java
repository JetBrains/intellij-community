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
    <caret>
}