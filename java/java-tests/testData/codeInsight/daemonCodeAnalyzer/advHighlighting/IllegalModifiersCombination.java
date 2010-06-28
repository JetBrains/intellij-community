// illegal modifier combinations

abstract public class a {
  //////////////////// fields ////////////////////////////////
  <error descr="Illegal combination of modifiers: 'public' and 'protected'">public</error> static
  <error descr="Illegal combination of modifiers: 'protected' and 'public'">protected</error> int f1 = 0;

  <error descr="Illegal combination of modifiers: 'public' and 'private'">public</error> volatile
  <error descr="Illegal combination of modifiers: 'private' and 'public'">private</error> int f2 = 0;

  <error descr="Illegal combination of modifiers: 'protected' and 'private'">protected</error> final
  <error descr="Illegal combination of modifiers: 'private' and 'protected'">private</error> int f3 = 0;

  <error descr="Illegal combination of modifiers: 'final' and 'volatile'">final</error>
  <error descr="Illegal combination of modifiers: 'volatile' and 'final'">volatile</error> private int f4 = 0;

  <error descr="Illegal combination of modifiers: 'public' and 'public'">public</error> 
  <error descr="Illegal combination of modifiers: 'public' and 'public'">public</error>
     int f5 = 0;

  public static final int cf1 = 0;
  static volatile private int cf2;
  transient public static final int cf3 = 0;
  protected volatile transient int cf4;
  private static final int cf5 = 1;



  ///////////////////// methods ///////////////////////////////////

  <error descr="Illegal combination of modifiers: 'abstract' and 'native'">abstract</error>  
  <error descr="Illegal combination of modifiers: 'native' and 'abstract'">native</error>  void m1();

  <error descr="Illegal combination of modifiers: 'static' and 'abstract'">static</error>  public
  <error descr="Illegal combination of modifiers: 'abstract' and 'static'">abstract</error>  void m2();

  <error descr="Illegal combination of modifiers: 'final' and 'abstract'">final</error>
  <error descr="Illegal combination of modifiers: 'abstract' and 'final'">abstract</error>  void m3();

  <error descr="Illegal combination of modifiers: 'private' and 'public'">private</error> static
  <error descr="Illegal combination of modifiers: 'public' and 'private'">public</error>  void m4() {}

  <error descr="Illegal combination of modifiers: 'protected' and 'private'">protected</error> final
  <error descr="Illegal combination of modifiers: 'private' and 'protected'">private</error>  void m5() {}

  <error descr="Illegal combination of modifiers: 'public' and 'public'">public</error> 
  <error descr="Illegal combination of modifiers: 'public' and 'public'">public</error> void m6() {};

  public abstract void cm1();
  protected static synchronized native void cm2();
  public static final void cm3() {}


  ///////////////////////// classes //////////////////////////////////
  <error descr="Illegal combination of modifiers: 'final' and 'abstract'">final</error>  static strictfp protected
  <error descr="Illegal combination of modifiers: 'abstract' and 'final'">abstract</error>  class c1 {}

  <error descr="Illegal combination of modifiers: 'private' and 'public'">private</error> final
  <error descr="Illegal combination of modifiers: 'public' and 'private'">public</error>  class c2 {}

  <error descr="Illegal combination of modifiers: 'final' and 'final'">final</error> 
  <error descr="Illegal combination of modifiers: 'final' and 'final'">final</error> class c3 {}

  abstract protected static strictfp class cc1 {}
  final private static class cc2 {}
  class cc3 {}
  static class cc4 {}

  ///////////////////////// locals
  void f() {
    <error descr="Illegal combination of modifiers: 'final' and 'final'">final</error>
    <error descr="Illegal combination of modifiers: 'final' and 'final'">final</error> int loc;
  }
}
