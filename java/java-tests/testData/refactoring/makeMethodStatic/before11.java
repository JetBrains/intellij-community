public class Foo {
    public int myData;
    int <caret>method(int i) {
        return myData + i;
    }
}

public class Bar extends Foo {
    int method(int b) {
        return super.method(b*2);
    }
}