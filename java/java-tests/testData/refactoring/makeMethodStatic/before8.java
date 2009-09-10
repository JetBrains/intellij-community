public class Foo {
    public int myData;
    int <caret>method(int i) {
        new Runnable () {
            void f() {};
            public void run() {
                this.f(myData);    
            }
        }
        return this.myData + myData;
    }
}