import java.lang.annotation.*;

@Target(ElementType.TYPE_USE)
@interface N {}
class A {

    private @N String par;

    void m(@N String par) {
        this.<caret>par = par;
    }
}