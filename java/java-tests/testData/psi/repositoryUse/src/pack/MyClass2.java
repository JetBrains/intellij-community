package pack;

public class MyClass2 extends String implements Runnable{
  /**
   * @deprecated
   */
  int field1 = 0;

  /// @deprecated The annotation is missing so no real deprecation
  Object field2[];

  java.lang.Object[] field3;

  void method1(int[] p1, Object p2, pack . AAA p3,
	pack . /* comment */ AAA p4,
	pack . // comment
	AAA p5
	) throws Exception, java.io. IOException{
  }

  Integer method2()[]{
    new Cloneable(){
    };
    class Local implements Cloneable{}
  }

  MyClass2(){}

  static class Inner{}
}
