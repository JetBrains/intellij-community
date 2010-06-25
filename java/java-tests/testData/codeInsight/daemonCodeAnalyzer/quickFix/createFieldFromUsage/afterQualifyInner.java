// "Create Field 'field'" "true"
class A{
    private Outer.Inner field<caret>;

    {
        Outer.f(field);
    }
}

class Outer{
    static class Inner{}

    static void f(Inner inner){}
}