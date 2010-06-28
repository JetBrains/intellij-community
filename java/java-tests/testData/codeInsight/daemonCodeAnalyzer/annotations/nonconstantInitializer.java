@interface Ann {
    int i ();
}

class D {
    int field;
    @Ann(i=<error descr="Attribute value must be constant">field</error>) void foo () {}
}

@interface ManistaDouble
{
    public abstract double defaultValue() default Double.NaN; 
}

