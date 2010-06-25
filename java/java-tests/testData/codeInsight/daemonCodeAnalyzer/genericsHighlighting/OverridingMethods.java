import java.util.*;

abstract class C<T> {
    abstract T f(int t);
    void ff(T t) {}

    C covariant1() { return null; }
    C covariant2() { return null; }
    <A> A get() { return null; }
}
abstract class D<U> extends C<C<U>> {
    abstract <error descr="'f(int)' in 'D' clashes with 'f(int)' in 'C'; attempting to use incompatible return type">U</error> f(int t);

    // overloaded, not overrridden
    int ff(int u) { return 0; }

    <error descr="'ff(C<U>)' in 'D' clashes with 'ff(T)' in 'C'; attempting to use incompatible return type">int</error> ff(C<U> u) {
      return 0;
    }

    <error descr="'covariant1()' in 'D' clashes with 'covariant1()' in 'C'; attempting to use incompatible return type">Object</error> covariant1() { return null; }
    D covariant2() { return null; }

    <A> A get() { return null; }
}


abstract class C1<T> {
    abstract T f(int t);
}
abstract class D1<U> extends C1<C1<U>> {
    abstract C1<U> f(int i);
}


class CC<T> {
 CC<Integer> f() { return null; }
 CC<Integer> f2() { return null; }
 CC<Integer> f3() { return null; }


 <K,V> K f(V v) { return null; }
 int fPrimitive() { return 0; }
}
class DD<T> extends CC<T> {
 <error descr="'f()' in 'DD' clashes with 'f()' in 'CC'; attempting to use incompatible return type">DD<String></error> f() { return null; }

 DD<Integer> f2() { return null; }
 CC f3() { return null; }

 <P,O> <error descr="'f(O)' in 'DD' clashes with 'f(V)' in 'CC'; attempting to use incompatible return type">O</error> f(O o) { return null; }

 // incompatible although assignable
 <error descr="'fPrimitive()' in 'DD' clashes with 'fPrimitive()' in 'CC'; attempting to use incompatible return type">double</error> fPrimitive() { return 0; }
}

interface Gen<T> {
 <K1 extends T> void f(Gen<K1> cc);
}
class Raw implements Gen {
 public void f(Gen o) {
   abstract class MyComparator<T> {
      abstract int compare(T t, T t1);
   }
   // raw type implemetation
   new MyComparator() {
       public int compare(Object t, Object t1) {
           return 0;
       }
   };
 }
}

class Gen2<GT> implements Gen<GT> {
 public <K2 extends GT> void f(Gen<K2> o) {}
}

////////////// ERASURE CONFLICT
class A1 <T> {
    T id(T t) {
        return t;
    }
}
interface I1 <T> {
    T id(T t);
}
class A2 <T> extends A1<String> {
    <error descr="'id(T)' in 'A2' clashes with 'id(T)' in 'A1'; both methods have same erasure, yet neither overrides the other">T id(T t)</error> {
        return t;
    }
}
class A3 <T> extends A1<String> {
    <error descr="'id(Object)' in 'A3' clashes with 'id(T)' in 'A1'; both methods have same erasure, yet neither overrides the other">Object id(Object o)</error> {
        return o;
    }
}
<error descr="'id(T)' in 'A1' clashes with 'id(T)' in 'I1'; both methods have same erasure, yet neither overrides the other">class A4 extends A1<String> implements I1<Integer></error> {
  String id(String t)
    { return null;}
  public Integer id(Integer i)
    { return null; }
}

interface II1 <T> {
    T id(int t);
}
interface II2 <T> {
    T id(int t);
}
abstract class A5 implements II1<Integer>, II2<Integer> {}
<error descr="'id(int)' in 'II2' clashes with 'id(int)' in 'II1'; methods have unrelated return types">abstract class A6 implements II1<Integer>, II2<String></error> {}
abstract class A7 implements II1<Number>, II2<Integer>{}
abstract class A8 implements II1<Integer>, II2<Number>{}


abstract class HasGenericMethods<T> {
    abstract <P> void toArray(P[] p);
}
public class RawOverridesGenericMethods extends HasGenericMethods{
    public void toArray(Object[] ps) {
    }
}

class CloneTest {
    interface A {
        A dup();
    }
    interface B extends A {
        B dup();
    }
    interface C extends A {
        C dup();
    }
    interface D extends B, C {
        D dup();
    }
    interface X extends C, B {
        X dup();
    }
    interface E extends C,A {
      E dup();
    }
}
///////////////
class ArrBase {
    C<String> getC() { return null; }
    Object[] getO() { return null; }
}
class ArrTest extends ArrBase {
    C getC() { return null; }
    String[] getO() { return null; }
}

///////////
class BarIU {
    public <B> B[] toArray(B[] ts) {
        return null;
    }
}
interface IU {
    public <I> I[] toArray(I[] ts);
}
public class BarIUBarIU extends BarIU implements IU{
    public <T> T[] toArray(T[] ts) {
        return null;
    }
}
//////////////////////
class MyIterator<T> {
}
class AAA <A> {
    public MyIterator<A> iterator() {
        return null;
    }
}

interface III <I> {
    MyIterator<I> iterator();
}

class CCC <T> extends AAA<T> implements III<T> {
    public MyIterator<T> iterator() {
        return null;
    }
}
//////////////////////////////////
interface CloneCovariant {
    CloneCovariant clone();
}

interface ICloneCovariant extends CloneCovariant {
}

interface Cmp<T> {
    int compareTo (T t);
}
<error descr="Class 'Singleton' must either be declared abstract or implement abstract method 'compareTo(T)' in 'Cmp'">class Singleton<T1> implements Cmp<Singleton<T1>></error> {
  public <T2> int compareTo(Singleton<T1> t1) {
    return 0;
  }
}

class e<V>  {
    <T> void u (T t) {}
}

class f extends e<String> {
    //If we inherit by erasure, then no type parameters must be present
    <error descr="'u(Object)' in 'f' clashes with 'u(T)' in 'e'; both methods have same erasure, yet neither overrides the other"><T> void u (Object o)</error> {}
}

//SCR 41593, the following overriding is valid
interface q {
    q foo();
}

interface p {
    p foo();
}

class r implements q, p {
    public  r foo() {
        return null;
    }
}

//IDEADEV-2255: this overriding is OK
class Example {
    interface Property<T> {
        T t();
    }

    public static void main(String[] args) {
        new ValueChangeListener<Number>() {
            public <E extends Number> void valueChanged(Property<E> parent, E oldValue, E newValue) {
            }
        };
    }

    interface ValueChangeListener<T> {
        <E extends T> void valueChanged(Property<E> property, E oldValue, E newValue);
    }
}

//IDEADEV-3310: there is no hiding("static overriding") in this code thus no return-type-substitutability should be checked
class BaseClass {}

class SubClass extends BaseClass {}

class BaseBugReport {

    public static <T extends BaseClass>
    java.util.Set<T> doSomething() {
        return null;
    }
}

class SubBugReport extends BaseBugReport {

    public static <T extends SubClass>
    java.util.Set<T> doSomething() {
        return null;
    }
}

class First<T extends Number> {
    void m(T t) {
        System.out.println("A: " + t);
    }
}

class Second<S extends Integer> extends First<S> {
    //@Override
    void m(S t) {
        System.out.println("B: " + t);
    }
}

class Third extends Second<Integer> {
    <error descr="'m(Number)' in 'Third' clashes with 'm(T)' in 'First'; both methods have same erasure, yet neither overrides the other">void m(Number t)</error> {
        System.out.println("D#m(Number): " + t);
    }

    //@Override
    void m(Integer t) {
        System.out.println("D#m(Integer): " + t);
    }
}

//IDEADEV-4587: this code is OK
interface SuperA<T, E extends Throwable> {
    T method() throws E;
}

interface SuperB<T, E extends Throwable> {
    T method() throws E;
}

interface MyInterface<T, E extends Throwable> extends SuperA<T, E>, SuperB<T, E> {
}

//IDEADEV-2832
class IDEADEV2832Test {
    public static void main(String[] args) {
        Listener<String> dl = new <error descr="'listen(T)' in 'Listener' clashes with 'listen(Object)' in 'Anonymous class derived from Listener'; both methods have same erasure, yet neither overrides the other">Listener<String></error>() {
            public void listen(String obj) {
            }

            public void listen(Object obj) {
            }
        };
    }
}

interface Listener<T> {
    void listen(T obj);
}

//end of IDEADEV-2832

//IDEADEV-8393
class Super<A extends Collection> {
    public String sameErasure(final List<?> arg)
    {
      System.out.println("Int list");
      return null;
    }

}


final class Manista extends Super<Collection> {

    public  Collection sameErasure(final List<String> arg) {
        System.out.println("String list");
        return null;
    }
}
//end of IDEADEV-8393

///////////////////
public class Prim {
    Object g() {
        return null;
    }
}
class SPrim extends Prim {
    byte[] g() {
        return null;
    }
}

//IDEADEV-21921
interface TypeDispatcher<T,V> {
    public <S extends T> void dispatch(Class<S> clazz, S obj);
}
class DefaultDispatcher<T,V> implements TypeDispatcher<T,V> {
    public <S extends T> void dispatch(Class<S> clazz, S obj) {
    }
}
interface Node {
}

class BubbleTypeDispatcher extends DefaultDispatcher<Node, String> {
}
//end of IDEADEV-21921

////////////////////////////////////////
public class Bug2 extends SmartList<Bug2> implements Places  {
}
interface Places extends java.util.List<Bug2> {}

class SmartList<E> extends java.util.AbstractList<E>{
    public E get(int index) {
        return null;
    }

    public int size() {
        return 0;
    }
}
////////////////IDEADEV-23176
class ActionImplementation extends MyAbstractAction
{
  public void actionPerformed()
  {
    throw new RuntimeException();
  }
}
abstract class MyAbstractAction extends AbstractAction implements MyAction { }
interface MyAction extends Action, BoundBean { }
interface BoundBean {
  void addPropertyChangeListener();
  void removePropertyChangeListener();
}
interface Action extends ActionListener {
    public void addPropertyChangeListener();
    public void removePropertyChangeListener();
}
interface ActionListener {
    public void actionPerformed();
}
abstract class AbstractAction implements Action{
    public synchronized void addPropertyChangeListener() {
    }
    public synchronized void removePropertyChangeListener() {
    }
}
//////////////////////////////
class A extends BaseBuild implements SRunningBuild{
}

interface Build {
  boolean isPersonal();
}
class BaseBuild implements Build {
    public boolean isPersonal() {
        return false;
    }
}
interface HistoryBuild {
  boolean isPersonal();
}
interface SRunningBuild extends Build,HistoryBuild{ }
////////////////////////////////////

interface PsiReferenceExpression extends PsiElement, PsiJavaCodeReferenceElement{}

interface PsiJavaCodeReferenceElement extends Cloneable, PsiQualifiedReference{}
interface PsiQualifiedReference extends PsiElement {}

interface PsiElement {
    String toString();
}
///////////////////////IDEADEV-24300 ////////////////////////
public class ActionContext<A extends ContextAction> {
}
public abstract class ContextAction<AC extends ActionContext> {
	protected abstract void performAction(AC context);
}
public class OurAction extends TableContextAction<Object> {
	protected void performAction(TableContext<Object> context) {
	}
}
public class TableContext<TCP> extends ActionContext<TableContextAction<TCP>> {
}
public abstract class TableContextAction<RO> extends ContextAction<TableContext<RO>> {
}
///////////////////////////////IDEADEV-23176 /////////////////////
public interface MyListModel { }
public interface MyList {
    Object get(int i);
    int hashCode();
}
public interface MutableListModel extends MyListModel, MyList {
}
public class ListModelImpl {
    public Object get(int i) {
        return null;
    }
}
public class MutableListModelImpl extends ListModelImpl implements MutableListModel {
}
///////////////////////////////////////////////////////////////

public class InheritanceBug {
    interface A {
        Object clone();
    }

    interface B {

    }

    interface C extends A, B {

    }

    class X implements C {
        public Object clone() {
            return null;
        }
    }

    class Y extends X {
    }
}
///////////////////////////////////////
class ideadev {
interface A {
    A f();
}

interface B extends A {
    B f();
}

interface C extends  A,B {

}

class s implements C {
    public <error descr="'f()' in 'ideadev.s' clashes with 'f()' in 'ideadev.B'; attempting to use incompatible return type">A</error> f() {
        return null;
    }
}
class sOk implements C {
    public  B f() {
        return null;
    }
}

}