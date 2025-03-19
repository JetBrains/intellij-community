public class TestImplImpl extends TestImpl {
  boolean foo(){
    return true;
  }

  boolean bar(){
   return !foo();
  }
}