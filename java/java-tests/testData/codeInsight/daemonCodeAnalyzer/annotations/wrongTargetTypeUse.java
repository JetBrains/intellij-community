import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Jaba {
  public static void accept(String <error descr="'@Ann' not applicable to type use">@Ann</error> [] s) {
  }
  public static void accept2(@Ann String[] s) {
  }
  public static void accept3(Jaba.<error descr="'@Ann' not applicable to type use">@Ann</error> Inner s) {
  }
  public static void accept4(@Ann Jaba.Inner s) {
  }
  
  class Inner {}
}

@Target({ElementType.PARAMETER})
@interface Ann {
}