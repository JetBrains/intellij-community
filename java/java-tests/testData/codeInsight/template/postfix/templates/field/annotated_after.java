@Target(ElementType.TYPE_USE)
@interface N {}
class A {

    private String par;

    void m(@N String par) {
        this.<caret>par = par;
    }
}