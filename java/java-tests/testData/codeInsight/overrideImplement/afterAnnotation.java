@interface ff{
    String f() default "";
}
class d implements ff {
    public String f() {
        <caret><selection>return null;  //To change body of implemented methods use File | Settings | File Templates.</selection>
    }

    public Class<? extends Annotation> annotationType() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
