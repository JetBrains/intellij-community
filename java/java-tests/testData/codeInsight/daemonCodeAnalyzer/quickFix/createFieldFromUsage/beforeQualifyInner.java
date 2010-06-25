// "Create Field 'field'" "true"
class A{
    {
        Outer.f(field<caret>);
    }
}

class Outer{
    static class Inner{}

    static void f(Inner inner){}
}