import java.lang.annotation.*;

@Target({ElementType.TYPE_USE/*, ElementType.TYPE*/})
@interface TA {

}

class C {
    void foo () {
        @TA C y = null;
        <selection>y</selection>;
    }
}
