// Not allowed in interface


interface A {
    <error descr="Not allowed in interface">A();</error>
    <error descr="Not allowed in interface">static {}</error>
    <error descr="Not allowed in interface">{}</error>
}

