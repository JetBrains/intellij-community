// throws conflicts on overriding/ override final
import java.io.*;

public class a extends c3 {
 public void f() throws <error descr="'f()' in 'a' clashes with 'f()' in 'c3'; overridden method does not throw 'java.lang.Exception'">Exception</error> {
 }
}
class SocketException extends IOException {
}
class ConnectException extends SocketException {
}

interface i {
 void f() throws SocketException;
}
class c2 implements i {
 public void f() throws <error descr="'f()' in 'c2' clashes with 'f()' in 'i'; overridden method does not throw 'java.io.IOException'">java.io.IOException</error> {}
}
class c2i implements i {
 public void f() throws <error descr="'f()' in 'c2i' clashes with 'f()' in 'i'; overridden method does not throw 'java.lang.Exception'">Exception</error> {}
}

class c3 implements i {
 public void f() throws ConnectException {}
}

class c4 extends c3 {
 public void f() throws ConnectException {}
}

interface MethodsFromObject {
  Object clone();
}
interface im extends MethodsFromObject {
}
<error descr="'clone()' in 'java.lang.Object' clashes with 'clone()' in 'MethodsFromObject'; overridden method does not throw 'java.lang.CloneNotSupportedException'">class cm implements MethodsFromObject</error> {
}

// sibling inheritance
class c5 { public void f() throws Exception {} }
interface i5 { void f(); }
<error descr="'f()' in 'c5' clashes with 'f()' in 'i5'; overridden method does not throw 'java.lang.Exception'">class c6 extends c5 implements i5</error> {
}

// overriding method does not throw exception, its OK
class c {
    protected Object clone() {
        return null;
    }
}
interface i6 {

}
class b extends c implements i6 {

}


//-------------- methods with same signature
interface AContract
{
  void invoke () throws Exception;
}

class A implements AContract
{
  public void invoke () throws Exception { }
}

interface BContract
{
  void invoke ();
}

class B extends A implements BContract
{
  public void invoke () { }
}

class C extends B 
{
}


//////////////////////
class Bug extends AbstrColl implements java.io.Serializable {
}
interface Coll  {
    boolean equals(Object f);
}
class AbstrColl implements Coll {}

interface InterfaceOverridesObject {
    Object clone() throws java.io.IOException;
}

////////////multiple interface inheritance///////
class sqlExcept extends Exception {}
class sqlInheritorException extends sqlExcept {}
class timeoutEx extends Exception{}

interface AMultiple {
  void m() throws java.io.IOException, sqlInheritorException;
}

interface BMultiple {
  void m() throws java.io.EOFException, sqlExcept, timeoutEx;
}

interface DMultiple extends AMultiple, BMultiple {}

interface WithStatic {
  static void f() {}
}

class WithStaticImpl implements WithStatic {
  <error descr="Static methods cannot be annotated with @Override">@Override</error>
  static void f() {}
}