public class Foo {
    public int myData;
    static int method(int i) {
        return myData + i;
    }
}

class Bar {
    public Foo myFoo;    
    int a(int b) {
        return Foo.method(b * 2);
    }
}