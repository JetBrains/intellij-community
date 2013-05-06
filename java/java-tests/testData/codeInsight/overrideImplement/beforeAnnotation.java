@interface ff{
    String f() default "";
}
class d implements ff {
    <caret>
    public Class<? extends Annotation> annotationType() {
        return null;
    }
}
