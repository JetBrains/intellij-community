// "Create field 'field'" "true-preview"
class A{
    private final Outer.Inner field<caret>;

    {
        Outer.f(field);
    }
}

class Outer{
    static class Inner{}

    static void f(Inner inner){}
}