import java.util.Collection;

class Bar {
    public static final Collection<Foo> foos;
}

class Foo {

    {
        Foo[] f = Bar.foos.toArray(new Foo[0]);<caret>
    }

}
