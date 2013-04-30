class A<T> {
    {
       class T extends A<T> {}
    }
    class T extends A<T> {}
}