class C
{
    Object x = new <error descr="Anonymous class implements interface; cannot have type arguments"><Integer></error> D() { };
    Object x1 = new <Integer> P() { };
    Object x2 = new <Integer> U() { };
    Object x22 = new <error descr="Method 'UU()' does not have type parameters"><Integer></error> UU() { };
    Object x3 = new <error descr="Anonymous class implements interface; cannot have type arguments"><Integer></error> I() { };
    interface D{}
    abstract class P {}
}

interface I {}
class U {}
class UU {
  UU() {}
}