import java.lang.annotation.*;

class <error descr="'record' is a restricted identifier and cannot be used for type declarations">record</error> {
  void x(<error descr="Illegal reference to restricted type 'record'">record</error> r) {}
}
record <error descr="Record has no header declared">NoComponentList</error> {}
record NoComponents() {}
class ClassWithComponents<error descr="Record header declared for non-record">(int x)</error> {}
class ClassWithComponents2<error descr="Record header declared for non-record">(int x, int y)</error> {}
<error descr="Modifier 'abstract' not allowed here">abstract</error> record AbstractRecord() {}
record ExtendsObject() <error descr="No extends clause allowed for record">extends Object</error> {}
record PermitsObject() <error descr="No permits clause allowed for record">permits Object</error> {}
class ExtendsRecord extends <error descr="Cannot inherit from final 'NoComponents'">NoComponents</error> {}
abstract class ExtendsJLR extends <error descr="Classes cannot directly extend 'java.lang.Record'">Record</error> {}
class AnonymousExtendsJLR {
  Record r = new <error descr="Classes cannot directly extend 'java.lang.Record'">Record</error>() {
    public boolean equals(Object other) {return this == other;}
    public int hashCode() {return 0;}
    public String toString() {return "";}
  };
}
<error descr="Class 'SuperInterface' must implement abstract method 'run()' in 'Runnable'">record SuperInterface() implements Runnable</error> {}
interface I1 { default void run() {}}
interface I2 { void run();}
record <error descr="Class 'UnrelatedDefaults' must implement abstract method 'run()' in 'I2'">UnrelatedDefaults</error>() implements I1, I2 {}

record ComponentModifiers(
  <error descr="Modifier 'public' not allowed here">public</error> int x, 
  <error descr="Modifier 'static' not allowed here">static</error> int y,
  <error descr="Modifier 'final' not allowed here">final</error> int z) {}
record ComponentDuplicateName(int <error descr="Variable 'x' is already defined in the scope">x</error>, int <error descr="Variable 'x' is already defined in the scope">x</error>) {}
record VarArgOk(int... x) {}
record VarArgOk2(int x, int... y) {}
record VarArgNotOk(<error descr="Vararg record component must be the last in the list">int... x</error>, int y) {}
record IllegalComponentName(
  int <error descr="Illegal record component name 'clone'">clone</error>, 
  int <error descr="Illegal record component name 'finalize'">finalize</error>, 
  int <error descr="Illegal record component name 'getClass'">getClass</error>, 
  int <error descr="Illegal record component name 'hashCode'">hashCode</error>, 
  int <error descr="Illegal record component name 'notify'">notify</error>, 
  int <error descr="Illegal record component name 'notifyAll'">notifyAll</error>, 
  int <error descr="Illegal record component name 'toString'">toString</error>, 
  int <error descr="Illegal record component name 'wait'">wait</error>) {}

@interface SimpleAnno {}
@Target(ElementType.CONSTRUCTOR)
@interface ConstructorAnno {}
@Target(ElementType.METHOD)
@interface MethodAnno {}
record AnnotatedComponents(
  @SimpleAnno int x, 
  <error descr="'@ConstructorAnno' not applicable to record component">@ConstructorAnno</error> int y,
  @MethodAnno int z) {}
  
class Outer {
  record NestedRecord() {}
  class Inner {
    <error descr="Static declarations in inner classes are not supported at language level '15'">record InnerRecord()</error> {}
  }
}

record ProhibitedMembers() {
  <error descr="Instance field is not allowed in record">int x = 5;</error>
  
  <error descr="Instance initializer is not allowed in record">{
    System.out.println("initializer");
  }</error>
  <error descr="Modifier 'native' not allowed here">native</error> void test();
}
record StaticFieldCollides(int i) {
  static int <error descr="Variable 'i' is already defined in the scope">i</error>;
}
record Incomplete(@<error descr="Class reference expected">i</error>nt a) {}
record CStyle(int a<error descr="C-style record component declaration is not allowed">[]</error>) {}
record CStyle2(int[] a<error descr="C-style record component declaration is not allowed">[] []</error> ) {}
record JavaStyle(int[] [] a) {}
record SafeVarargComponent(<error descr="@SafeVarargs annotation cannot be applied for a record component">@SafeVarargs</error> int... component) {}