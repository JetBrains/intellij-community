public class Foo {
    static Foo
            f1 = new Foo(){
                public String toString() {
                    return <selection>"a" + "b"</selection>;
                }
            },
            f2 = new Foo(){};

}