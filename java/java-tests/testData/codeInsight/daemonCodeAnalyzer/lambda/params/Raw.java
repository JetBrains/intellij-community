class Test {
  {
    <error descr="Incompatible types. Found: '<lambda expression>', required: 'java.lang.Comparable'">Comparable c = (String o)->{
      return 0;
    };</error>
  }
}