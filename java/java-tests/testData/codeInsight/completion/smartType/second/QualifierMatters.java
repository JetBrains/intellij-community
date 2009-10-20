class Goo {}
class Foo {
    public static final Bar<Goo> BAR;


}

class Bar<T> {
    T getGoo();

}

class Main {
    {

        Goo g = Foo.<caret>

    }
}