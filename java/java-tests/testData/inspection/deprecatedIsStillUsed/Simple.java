public class Simple {
  /**
   * @deprecated
   */
  int <warning descr="Deprecated member 'bbb' is still used">bbb</warning>;

  @Deprecated
  int <warning descr="Deprecated member 'bbb2' is still used">bbb2</warning>;

  @Deprecated
  int <warning descr="Deprecated member 'bbb3' is still used">bbb3</warning>() {
    return 0;
  }

  @Deprecated
  class <warning descr="Deprecated member 'Bbb4' is still used">Bbb4</warning> {

  }

  int use(){
    return bbb + bbb2 + bbb3() + Bbb4.class.toString().hashCode();
  }


  //////////////////////////////

  @Deprecated
  int ddd2;

  @Deprecated
  int ddd3() {
    return 0;
  }

  @Deprecated
  class Ddd4 {

  }

  @Deprecated
  int useFromDeprecated(){
    return ddd2 + ddd3() + Ddd4.class.toString().hashCode();
  }

  /**
   * {@link #useFromJavadoc}
   */
  void withJavadocReference() {}
  @Deprecated
  void useFromJavadoc() { }
}