import java.util.Iterator;
import java.util.List;

class A<<warning descr="Type parameter 'T' is never used">T</warning>> {
  List<String> getList() {
    return null;
  }
}

public class DefaultEventSource<<warning descr="Type parameter 'T' is never used">T</warning>> extends A {
  public Iterator<String> getKeys(){
    return null;
  }

  @Override
  List<String> getList() {
    return <warning descr="Unchecked assignment: 'java.util.List' to 'java.util.List<java.lang.String>'. Reason: 'super' has raw type, so result of getList is erased">super.getList()</warning>;
  }

  void f(DefaultEventSource source){
    final Iterator<String> <warning descr="Variable 'keys' is never used">keys</warning> =  <warning descr="Unchecked assignment: 'java.util.Iterator' to 'java.util.Iterator<java.lang.String>'. Reason: 'source' has raw type, so result of getKeys is erased">source.  getKeys()</warning>;
    final Iterator<String> <warning descr="Variable 'keys1' is never used">keys1</warning> = <warning descr="Unchecked cast: 'java.util.Iterator' to 'java.util.Iterator<java.lang.String>'. Reason: 'source' has raw type, so result of getKeys is erased">(Iterator<String>)source.getKeys()</warning>;
    final Iterator<String> <warning descr="Variable 'keys2' is assigned but never accessed">keys2</warning>;
    keys2 = <warning descr="Unchecked assignment: 'java.util.Iterator' to 'java.util.Iterator<java.lang.String>'. Reason: 'source' has raw type, so result of getKeys is erased">source.getKeys()</warning>;

    for (<error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.Object'">String <warning descr="Parameter 'o' is never used">o</warning></error> : super.getList()) {}
  }
}

