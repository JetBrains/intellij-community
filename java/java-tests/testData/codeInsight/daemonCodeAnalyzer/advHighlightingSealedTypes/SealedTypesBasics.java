//classes
sealed class A permits B {}
sealed class B extends A permits C, D {}
final class C extends B {}
non-sealed class D extends B {}
class E extends <error descr="'E' is not allowed in the sealed hierarchy">A</error> {}
sealed <error descr="Repeated modifier 'sealed'">sealed</error> class SealedSealed {}
<error descr="Illegal combination of modifiers 'sealed' and 'non-sealed'">sealed</error> <error descr="Illegal combination of modifiers 'non-sealed' and 'sealed'">non-sealed</error> class SealedNonSealed {}
<error descr="Illegal combination of modifiers 'sealed' and 'final'">sealed</error> <error descr="Illegal combination of modifiers 'final' and 'sealed'">final</error> class SealedFinal {}

//interfaces
sealed interface IA permits IB, IC {}
final class IB implements IA {}
sealed interface IC extends IA {}
class <error descr="Modifier 'sealed', 'non-sealed' or 'final' expected">ICSameFile</error> implements IC {}
sealed interface Foos {
  interface <error descr="Modifier 'sealed' or 'non-sealed' expected">Bar</error> extends Foos { // here
  }
}

sealed interface ID0 {}
non-sealed interface ID1 extends ID0 {}
<error descr="Modifier 'non-sealed' not allowed on classes that do not have a sealed superclass">non-sealed</error> interface ID extends ID1 {}

enum ImplicitlySealedEnum {
  A {}
}

<error descr="Modifier 'sealed' not allowed here">sealed</error> enum Foo {}

<error descr="Modifier 'sealed' not allowed here">sealed</error> enum WithConstants {
  BAR {}
}

<error descr="Modifier 'sealed' not allowed here">sealed</error> @interface MyAnnoType {}

//records
sealed interface IWithRecords permits WithRecords { }
record WithRecords() implements IWithRecords {}