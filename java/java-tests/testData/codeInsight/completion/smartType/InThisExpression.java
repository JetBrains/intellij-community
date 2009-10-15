public class Foo {
    
    Runnable r = new Runnable() {
        public void run() {
            Foo f = <caret>Foo.this;
        }
    };
}
