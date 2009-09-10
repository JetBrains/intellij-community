public class Foo {
            
    public int f(int i) {
        return 0;
    }

    public static int <caret>method(Foo anObject, int i) {
        return anObject.f(i);
    }
}