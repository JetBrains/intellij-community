abstract public class YYY implements I,II{
    private void f(YYY y) {
        II i = y.<caret>foo();
    }
}
interface I {
    I foo();
}
interface II extends I {
    II foo();
}
