import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.util.*;

@Target({ElementType.TYPE_USE/*, ElementType.TYPE*/})
@interface TA {
}

@<error descr="'@TA' not applicable to type">TA</error>
class X0<@<error descr="'@TA' not applicable to type parameter">TA</error> T> {
    @TA
    protected
    void f() @<error descr="'@TA' not applicable to parameter">TA</error>  {
        @TA String p=new @TA String();

        if (this instanceof @TA Object) return;
        String o = p;
        List<@TA String> l;
        Class c = @TA String.class;
    }

    @TA int @TA[] methodf() throws @TA Exception {
        boolean a = this instanceof @TA X0;
        X0<@<error descr="Duplicate annotation">TA</error> @<error descr="Duplicate annotation">TA</error> X0> c = new @TA X0<@TA X0>();
        Object o = (@TA Object) c;
        @TA X0.field = null;
        return null;
    }

    @TA() int @TA[] p;
    static @TA Object field;
    @TA List<String> disambiguateBetweenBinaryExpr;
}
