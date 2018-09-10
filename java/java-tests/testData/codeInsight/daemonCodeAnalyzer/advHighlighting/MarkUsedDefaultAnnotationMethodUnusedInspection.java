@Annotation("")
class <warning descr="Class 'Bar' is never used">Bar</warning> { }
@interface Annotation {
    String value();
    int <warning descr="Method 'ints()' is never used">ints</warning>() default 0;
}