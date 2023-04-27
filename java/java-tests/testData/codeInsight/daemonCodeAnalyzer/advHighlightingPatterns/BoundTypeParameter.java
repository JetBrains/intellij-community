public class BoundTypeParameter {
  interface BaseRecord<T> {}
  record ExtendedRecord1<T extends CharSequence> (T a,T b) implements BaseRecord<T>{}


  public static void wildcardWiderBound1(BaseRecord<? extends String> variable1) //upper bound is Object???
  {
    if (variable1 instanceof ExtendedRecord1<?>(var a, var b)) {
      System.out.println(a + " " + b.length());
    } else {
      System.out.println("No luck");

    }
  }

  public static void testWildcardWiderBound1(){
    ExtendedRecord1<String> param = new ExtendedRecord1<>("Foo","Bar");
    wildcardWiderBound1(param);
  }
}