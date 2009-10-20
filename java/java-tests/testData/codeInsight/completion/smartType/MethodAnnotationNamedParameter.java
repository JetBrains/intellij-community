public class BarBaz {

    @Foo(isBar = t<caret>)
    public void x() {}

    public @interface Foo {
        boolean isBar() default false;
    }
}