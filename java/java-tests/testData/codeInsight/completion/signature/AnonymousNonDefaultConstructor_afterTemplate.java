abstract class Foo{
    public Foo(int x) {
    }

    abstract int foo();

    {
        Foo f = new Foo(x) {
            @Override
            int foo() {
                return 0;
            }
        }
    }
}