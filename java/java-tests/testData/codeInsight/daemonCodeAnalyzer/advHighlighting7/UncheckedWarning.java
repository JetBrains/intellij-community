import java.util.List;

class Test {
  {
    Class foo = Object.class;
    <warning descr="Unchecked call to 'isAssignableFrom(Class<?>)' as a member of raw type 'java.lang.Class'">foo.isAssignableFrom</warning>(Object.class);
  }

  public List<String> transform(List<List<String>> result) {
    <error descr="Incompatible types. Found: 'java.util.List<java.util.List<java.lang.String>>', required: 'java.util.List<java.lang.String>'">return result;</error>
  }
}