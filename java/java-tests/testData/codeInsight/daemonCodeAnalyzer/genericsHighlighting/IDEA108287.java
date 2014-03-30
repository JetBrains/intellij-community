class Foo<T> {
    static class Nested {};
}
class Bar extends Foo<<error descr="Nested is not accessible in current context">Bar.Nested</error>> {}

interface FooI<T> {
    interface Nested {};
}
interface BarI extends FooI<<error descr="Nested is not accessible in current context">BarI.Nested</error>> {}
