public class TestImpl extends Test{
  boolean foo(){
   return super.foo();
  }

  boolean bar(){
   return !foo();
  }
}
