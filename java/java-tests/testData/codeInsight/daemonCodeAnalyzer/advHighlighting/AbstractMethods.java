// abstract methods
public class a {
  <error descr="Missing method body, or declare abstract">void f();</error>

}
abstract class c1 {
  abstract void f1();
  <error descr="Missing method body, or declare abstract">int f2();</error>
}

interface ff {
  abstract void f1();
  void f2();
}

class x {
  void f() {
    <error descr="Method call expected">RuntimeException()</error>;
    throw <error descr="Method call expected">RuntimeException()</error>;
  }
}

// -------------------------------------------------------------
<error descr="Class 'c2' must either be declared abstract or implement abstract method 'f()' in 'c2'">class c2</error> {
  <error descr="Abstract method in non-abstract class">abstract</error> void f();
}

<error descr="Class 'c3' must either be declared abstract or implement abstract method 'iif()' in 'c4'">class c3 extends c4</error> {

}

abstract class c4 {
  abstract void iif();
}

class c5 extends c6 implements i7 { public void ff(){} }
abstract class c6 {}
interface i7 { void ff(); }

<error descr="Class 'c7' must either be declared abstract or implement abstract method 'ff()' in 'i7'">class c7 implements i7</error> {
}

class callabstract extends c4 {
  void iif() {
    <error descr="Abstract method 'iif()' cannot be accessed directly">super.iif()</error>;
  }
}


abstract class c8 {
    public abstract boolean equals(Object other);
}

<error descr="Class 'c9' must either be declared abstract or implement abstract method 'equals(Object)' in 'c8'">final class c9 extends c8</error> {
}



//------- if only Bottom were in other package, it should have been abstract --------------------------
public abstract class AbstractTest {
 
    abstract String getName();
 
    abstract static class Middle extends AbstractTest {
 
    }
 
    static class Bottom extends Middle {
        String getName() {
            return null;
        }
    }
}

///////////
abstract class cc1 {
  abstract void f(int i);
}
abstract class cc2 extends cc1 {
  abstract protected void f(int i);
}
class cc3 extends cc2 {
  public void f(int i) {}
}
///////////////
interface MyComparator {
    int compare(Object t, Object t1);

    boolean equals(java.lang.Object object);
}
class MyComparatorImpl implements MyComparator {
    public int compare(Object o, Object o1) {
        new MyComparator() {
            public int compare(Object o, Object o1) {
                return 0;
            }
        };
        return 0;
    }
}
//////////////// IDEADEV-6050
interface Comparable {}

interface PublicCloneable extends Cloneable {
    Object clone() throws CloneNotSupportedException;
}

interface PublicCloneableExtension extends  Comparable, PublicCloneable {
}
