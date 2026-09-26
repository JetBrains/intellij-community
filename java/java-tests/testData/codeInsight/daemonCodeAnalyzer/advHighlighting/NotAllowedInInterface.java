// Not allowed in interface


interface A {
    <error descr="Constructor is not allowed in interface">A();</error>
    <error descr="Class initializer is not allowed in interface">static {}</error>
    <error descr="Class initializer is not allowed in interface">{}</error>
}

