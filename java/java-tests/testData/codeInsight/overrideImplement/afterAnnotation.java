@interface ff{
    String f() default "";
}
class d implements ff {
    public String f() {
        <caret><selection>return "";</selection>
    }

    public Class<? extends Annotation> annotationType() {
        return null;
    }
}
