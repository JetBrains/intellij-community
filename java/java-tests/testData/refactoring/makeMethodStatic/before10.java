public class Foo {
    public int myData;
    int <caret>method(int i) {
        return myData + i;
    }
}

class Bar {
    public Foo myFoo;    
    int a(int b) {
        return myFoo.method(b * 2);
    }
}