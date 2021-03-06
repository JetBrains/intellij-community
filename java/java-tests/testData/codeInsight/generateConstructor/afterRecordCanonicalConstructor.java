import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

record Test(final int foo, @Anno1 @Anno2 @Anno3 @Anno4 double bar, String... varArg) {
    Test(int foo, @Anno2 @Anno3 @Anno4 double bar, String... varArg) {<caret>
        this.foo = foo;
        this.bar = bar;
        this.varArg = varArg;
    }

    public int foo() {
        return foo;
    }
}
@Target(ElementType.RECORD_COMPONENT)
@interface Anno1 { }
@Target({ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@interface Anno2 { }
@Target({ElementType.PARAMETER})
@interface Anno3 { }
@Target({ElementType.TYPE_USE})
@interface Anno4 { }
