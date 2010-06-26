public class Autoboxing {
    public boolean compare(short s, Integer i) {
         return i == s; //OK, i is unboxed
    }

    public boolean compare(Short s, Integer i) {
         return <error descr="Operator '==' cannot be applied to 'java.lang.Integer','java.lang.Short'">i == s</error>; //comparing as references 
    }

    void f(Integer i) {
      switch(i) {
       default:
      }
    }

    {
      Object data = 1;
      boolean is1 = <error descr="Operator '==' cannot be applied to 'java.lang.Object','int'">data == 1</error>;
    }

    //IDEADEV-5549: Short and double are convertible
    public static double f () {
      Short s = 0;
      return (double)s;
    }

    //IDEADEV-5613
    class DumbTest {
      private long eventId;

      public int hashCode() {
        return ((Long) eventId).hashCode();
      }
    }

    public static void main(String[] args) {
        Long l = 0L;
        Short s = 0;

        int d = <error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'int'">(int)l</error>;
        d = (int)s;

        short t = 0;
        Integer d1 = <error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Integer'">(Integer) t</error>;
        Byte b = <error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Byte'">(Byte) t</error>;

    }

  {
    {
      boolean cond = true;
      // test for JLS3 bug, see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6888770
      Byte B = 0;
      byte b = 0;
      byte value = cond ? B : b;  /////////

      short s = 0;
      Short S = 0;
      short rs = cond ? S : s;

      char c = 0;
      Character C = 0;
      char rc = cond ? C : c;

      boolean bb = cond ? Boolean.FALSE : true;
    }
    
  }
}