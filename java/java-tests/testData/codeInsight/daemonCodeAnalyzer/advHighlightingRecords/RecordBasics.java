import java.lang.annotation.*;

record <error descr="Record has no header declared">NoComponentList</error> {}
record NoComponents() {}
class ClassWithComponents<error descr="Record header declared for non-record">(int x)</error> {}
class ClassWithComponents2<error descr="Record header declared for non-record">(int x, int y)</error> {}
<error descr="Modifier 'abstract' not allowed here">abstract</error> record AbstractRecord() {}
record ExtendsObject() <error descr="No extends clause allowed for record">extends Object</error> {}
class ExtendsRecord extends <error descr="Cannot inherit from final 'NoComponents'">NoComponents</error> {}

record ComponentModifiers(
  <error descr="Modifier 'public' not allowed here">public</error> int x, 
  <error descr="Modifier 'static' not allowed here">static</error> int y,
  final int z) {}
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
    <error descr="Inner classes cannot have static declarations">record InnerRecord()</error> {}
  }
}

record ProhibitedMembers() {
  <error descr="Instance field is not allowed in record">int x = 5;</error>
  
  <error descr="Instance initializer is not allowed in record">{
    System.out.println("initializer");
  }</error>
  <error descr="Modifier 'native' not allowed here">native</error> void test();
}