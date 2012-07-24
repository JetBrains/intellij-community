class XXX {
  Runnable bar() {
    <error descr="Incompatible types. Found: '<lambda expression>', required: 'java.lang.Runnable'">return (o)->{
      System.out.println();
    };</error>
  }
}