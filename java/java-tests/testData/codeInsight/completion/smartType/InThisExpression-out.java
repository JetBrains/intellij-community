public class Foo {
    
    Runnable r = new Runnable() {
        public void run() {
            Foo f = Foo.this;<caret>Foo.this;
        }
    };
}
