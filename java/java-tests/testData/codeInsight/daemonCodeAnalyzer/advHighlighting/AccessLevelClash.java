// access level clashes
interface i {
 void ff();
}

public class a implements i {
 void <error descr="'ff()' in 'a' clashes with 'ff()' in 'i'; attempting to assign weaker access privileges ('packageLocal'); was 'public'">ff</error>() {}
}
class ai implements i {
 public <error descr="'ff()' in 'ai' clashes with 'ff()' in 'i'; attempting to use incompatible return type">int</error> ff() { return 0;}
}

class c2 implements i {
 public c2() {}
 public void ff() {}
 protected void g() {}
 private int fff(String s) { return 0; }
}

class c3 extends c2 {
 protected c3() {}
 private int g(int k) { return 2;}
 private char fff(String s) { return 0; }
}

class c4 extends c3 {
 private c4() {}
 <error descr="'g()' in 'c4' clashes with 'g()' in 'c2'; attempting to assign weaker access privileges ('private'); was 'protected'">private</error> void g() {}
 private String fff(String s) throws java.io.IOException { return null; }
}
class c4i extends c3 {
 protected <error descr="'g()' in 'c4i' clashes with 'g()' in 'c2'; attempting to use incompatible return type">Object</error> g() {return null;}
}

// sibling inheritance
abstract class c5 { abstract public int ff(); }
interface i5 { void ff(); }
<error descr="'ff()' in 'i5' clashes with 'ff()' in 'c5'; methods have unrelated return types">abstract class c6 extends c5 implements i5</error> {
}

class c7 { public String ff() { return null;} }
<error descr="'ff()' in 'c7' clashes with 'ff()' in 'i5'; attempting to use incompatible return type">class c8 extends c7 implements i5</error> {
}

// interface should not clash with Object
interface A {
    Object clone() throws CloneNotSupportedException;
    void finalize();

    <error descr="'hashCode()' in 'A' clashes with 'hashCode()' in 'java.lang.Object'; attempting to use incompatible return type">void</error> hashCode();
    <error descr="'equals(Object)' in 'A' clashes with 'equals(Object)' in 'java.lang.Object'; attempting to use incompatible return type">void</error> equals(Object o);
    <error descr="'toString()' in 'A' clashes with 'toString()' in 'java.lang.Object'; attempting to use incompatible return type">void</error> toString();
}

interface ConflictWithObject {
        Object clone() throws CloneNotSupportedException;
}
<error descr="'clone()' in 'java.lang.Object' clashes with 'clone()' in 'ConflictWithObject'; attempting to assign weaker access privileges ('protected'); was 'public'">class s implements ConflictWithObject</error> {

}

// parallel overriding methods from Object
interface InderFace {
  Object clone() throws CloneNotSupportedException;
}

interface SubInderFace extends InderFace {
}

<error descr="'clone()' in 'java.lang.Object' clashes with 'clone()' in 'InderFace'; attempting to assign weaker access privileges ('protected'); was 'public'">class Implementation implements SubInderFace</error> {
}



//SCR20002
abstract class SCR20002A extends Object implements Runnable {
  protected abstract int getSome();
  private final Inner getInner() { return null; }
  private class Inner { }
}

abstract class SCR20002B extends SCR20002A implements Runnable {
  private final Inner getInner() { return null; }
  private class Inner { }
}
abstract class SCR20002C extends SCR20002B implements Runnable {
}
