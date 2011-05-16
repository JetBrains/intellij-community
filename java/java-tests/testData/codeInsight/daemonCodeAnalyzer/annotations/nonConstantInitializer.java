@interface Ann {
    int i ();

    String[] j();
}

class D {
    int field;
    @Ann(i=<error descr="Attribute value must be constant">field</error>) void foo () {}

    @Ann(j={<error descr="Attribute value must be constant">null</error>}) void bar() {}
}

@interface ManistaDouble
{
    public abstract double defaultValue() default Double.NaN; 
}

