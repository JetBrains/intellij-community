class Foo<<warning descr="Type parameter 'T' is never used">T</warning>> {
    static class Nested {};
}
class Bar extends Foo<<warning descr="Nested is not accessible in current context">Bar.Nested</warning>> {}

interface FooI<<warning descr="Type parameter 'T' is never used">T</warning>> {
    interface Nested {};
}
interface BarI extends FooI<<warning descr="Nested is not accessible in current context">BarI.Nested</warning>> {}
