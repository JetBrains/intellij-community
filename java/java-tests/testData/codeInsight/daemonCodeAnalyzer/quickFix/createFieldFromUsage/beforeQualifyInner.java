// "Create field 'field'" "true-preview"
class A{
    {
        Outer.f(field<caret>);
    }
}

class Outer{
    static class Inner{}

    static void f(Inner inner){}
}