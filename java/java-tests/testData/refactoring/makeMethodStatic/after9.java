public class Foo {
    public int myData;
    static int <caret>method(Foo anObject, int i) {
        return anObject.myData + i;
    }
}

public class Bar extends Foo {
    int a(int b) {
        return method(this, b*2);
    }
}