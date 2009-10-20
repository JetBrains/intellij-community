public class BarBaz {

    @Foo(isBar = true<caret>)
    public void x() {}

    public @interface Foo {
        boolean isBar() default false;
    }
}