abstract class A {
    abstract <T> T foo();

    {
        int x = foo();
    }
}
