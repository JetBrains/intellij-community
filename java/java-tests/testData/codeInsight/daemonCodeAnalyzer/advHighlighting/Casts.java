// casts
public class a  {
 void f(i ii) {
   boolean b;
   b = <error descr="Inconvertible types; cannot cast 'int' to 'a'">2 instanceof a</error>;
   b = <error descr="Inconvertible types; cannot cast 'null' to 'int'">null instanceof int</error>;
   b = <error descr="Inconvertible types; cannot cast 'c3' to 'java.lang.Boolean'">(c3)null instanceof Boolean</error>;

   b = ii instanceof i;
   b = ((c2)ii) instanceof c4;
   b = null instanceof a;
   b = ii instanceof c3;
   b = Boolean.valueOf("true") instanceof Boolean;
   b = new Long(3) instanceof Number;
   b = this instanceof a;
   b = ii instanceof a;
   b = this instanceof i;


   // casts
   c2 c2i = null;
   c3 c3i = null;
   c4 c4i = null;
   Object o;
   c4i = <error descr="Inconvertible types; cannot cast 'a' to 'c4'">(c4) this</error>;
   o   = <error descr="Inconvertible types; cannot cast 'c2' to 'boolean'">(boolean) c2i</error>;
   o   = <error descr="Inconvertible types; cannot cast 'c3' to 'java.lang.Integer'">(Integer) c3i</error>;
   o   = <error descr="Inconvertible types; cannot cast 'c2' to 'a'">(a) c2i</error>;
   o   = <error descr="Inconvertible types; cannot cast 'c3' to 'int'">(int) c3i</error>;

   o = (a) ii;
   o = (i) c4i; //cast to interface
   o = (c3) c2i;
   o = (c3) c3i;
   o = (c3) c4i;
   o = (Object) ii;
   o = (iunrelated) ii;
   o = (iunrelated) c2i;
   o = (c4) c2i;
   o = (c4) ii;
   o   = <error descr="Inconvertible types; cannot cast 'i' to 'c5'">(c5) ii</error>;

   int[] ai = null;
   o = <error descr="Inconvertible types; cannot cast 'int[]' to 'byte[]'">(byte[])ai</error>;
   o = <error descr="Inconvertible types; cannot cast 'int[]' to 'double[]'">(double[])ai</error>;
   c3[] ac3i = null;
   o = ac3i;
   o = (c4[])ac3i;
   o = (i[])ac3i;
   Object[] results = null;
   int index = (<error descr="Inconvertible types; cannot cast 'java.lang.Object[]' to 'java.lang.Integer'">(Integer) results</error>).intValue();


   // arrays and Serializable/Cloneable/Object
   int[] ai2 = (int[])o;
   Cloneable cloneable = null;
   ai2 = (int[]) cloneable;
   java.io.Serializable serializable = null;
   ai2 = (int[]) serializable;

 }
 
}

interface iunrelated {}
interface i {}
class c2 implements i {
 public c2() {}
 public void f() {}
 protected void g() {}
}

class c3 extends c2 {
 protected c3() {}
 private int g(int k) { return 0; }
}

final class c4 extends c3 {
 private c4() {
        int[] a=new int[3];
        Cloneable s=a; 
        java.io.Serializable ss = a;
 }
}
final class c5 {}

// clashing interfaces
interface A {
    void g();
}
interface B {
    int g();
}
interface BB extends B {
}
class Foo {
    void f(A a) {
        B b = <error descr="Inconvertible types; cannot cast 'A' to 'B'">(B) a</error>;
        BB b2 = <error descr="Inconvertible types; cannot cast 'A' to 'BB'">(BB) a</error>;
        A a2 = <error descr="Inconvertible types; cannot cast 'BB' to 'A'">(A) b2</error>;
    }
}       
