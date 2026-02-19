@interface Ann {
    int i ();

    String[] j();
}

class D {
    int field;
    final int field1 = 1;
    @Ann(i=<error descr="Attribute value must be constant">field</error>) void foo () {}
    @Ann(i=<error descr="Attribute value must be constant">this.field1</error>) void foo1 () {}
    @Ann(i=field1, j = {}) void foo2 () {}

    @Ann(j={<error descr="Attribute value must be constant">null</error>}) void bar() {}
}

@interface ManistaDouble
{
    public abstract double defaultValue() default Double.NaN; 
}

