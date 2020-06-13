//classes
sealed class A permits B {}
sealed class B extends A permits C, D {}
final class C extends B {}
non-sealed class D extends B {}
class E extends A {}
<error descr="Illegal combination of modifiers: 'sealed' and 'sealed'">sealed</error> <error descr="Illegal combination of modifiers: 'sealed' and 'sealed'">sealed</error> class SealedSealed {}
<error descr="Illegal combination of modifiers: 'sealed' and 'non-sealed'">sealed</error> <error descr="Illegal combination of modifiers: 'non-sealed' and 'sealed'">non-sealed</error> class SealedNonSealed {}
<error descr="Illegal combination of modifiers: 'sealed' and 'final'">sealed</error> <error descr="Illegal combination of modifiers: 'final' and 'sealed'">final</error> class SealedFinal {}

//interfaces
sealed interface IA permits IB, IC {}
final class IB implements IA {}
sealed interface IC extends IA {}
class ICSameFile implements IC {}
