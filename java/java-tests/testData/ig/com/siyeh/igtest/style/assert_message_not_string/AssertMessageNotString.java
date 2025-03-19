public class AssertMessageNotString {

  void f(int i) {
    assert true : "yup";
    assert false;
    assert true : <warning descr="'assert' message of type 'int'">i</warning>;
    assert i > 10 : <warning descr="'assert' message of type 'boolean'">true</warning>;
    assert i < 0 : <warning descr="'assert' message of type 'Boolean'">Boolean.TRUE</warning>;
  }
}