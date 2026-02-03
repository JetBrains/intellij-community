import java.lang.annotation.*;

@Target({ElementType.TYPE_USE})
@interface TA { }

class C {
    void foo () {
        @TA C y = null;
        <selection>y</selection>;
    }
}
