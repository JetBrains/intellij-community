public class Test{
  /**
   * @deprecated
   */
  int bbb;

  @Deprecated
  int bbb2;

  @Deprecated
  int bbb3() {
    return 0;
  }

  @Deprecated
  class Bbb4 {

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
}