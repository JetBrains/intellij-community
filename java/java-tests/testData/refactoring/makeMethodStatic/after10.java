public class Foo {
    public int myData;
    static int <caret>method(Foo anObject, int i) {
        return anObject.myData + i;
    }
}

class Bar {
    public Foo myFoo;    
    int a(int b) {
        return Foo.method(myFoo, b * 2);
    }
}