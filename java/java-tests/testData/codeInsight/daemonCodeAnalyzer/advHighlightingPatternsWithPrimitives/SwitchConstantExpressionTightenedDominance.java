public class SwitchConstantExpressionTightenedDominance {

  static void main() {

  }

  private static final long CONST_L = (long) (1 * 6);
  private static final Long CONST_LONG = (Long) (CONST_L);
  public static final float CONST_F = 1f + 2f + 3f;
  public static final float CONST_F_NAN = Float.NaN;
  public static final double CONST_D = (double) (7 * 1);
  public static final Double CONST_DOUBLE= (double) (7 * 2);

  public static void test_long(long l) {
    switch (l) {
      case <error descr="Incompatible types. Found: 'char', required: 'long'">'1'</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'int', required: 'long'">1</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case 1L -> System.out.println(1);
      case 1L + 2L -> System.out.println(1);
      case (long) (3 + 4) -> System.out.println(1);
      case CONST_L -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Constant expression required">CONST_LONG</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'long'">1f</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'double', required: 'long'">1.</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
  }

  public static void test_Long(Long l) {
    switch (l) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Long'">'1'</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">1</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case 1L -> System.out.println(1);
      case 1L + 2L -> System.out.println(1);
      case (long) (3 + 4) -> System.out.println(1);
      case CONST_L -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Constant expression required">CONST_LONG</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Long'">1f</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Long'">1.</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
  }

  public static void test_float(float l) {
    switch (l) {
      case <error descr="Incompatible types. Found: 'char', required: 'float'">'1'</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'int', required: 'float'">1</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'long', required: 'float'">1L</error> -> System.out.println(1); //error
      case <error descr="Incompatible types. Found: 'long', required: 'float'">CONST_L</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case 1f -> System.out.println(1);
      case 1f + 2f -> System.out.println(1);
      case CONST_F -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case 1f -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case 1f + 2f -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case CONST_F_NAN -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'double', required: 'float'">1.</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
  }

  public static void test_Float(Float l) {
    switch (l) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Float'">'1'</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Float'">1</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Float'">1L</error> -> System.out.println(1); //error
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Float'">CONST_L</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case 1f -> System.out.println(1);
      case 1f + 2f -> System.out.println(1);
      case CONST_F -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case 1f -> System.out.println(1);
      default -> System.out.println(l);
    }

    switch (l) {
      case 1f + 2f -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case CONST_F_NAN -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Float'">1.</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
  }

  public static void test_double(double l) {
    switch (l) {
      case <error descr="Incompatible types. Found: 'char', required: 'double'">'1'</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'int', required: 'double'">1</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'long', required: 'double'">1L</error> -> System.out.println(1); //error
      case <error descr="Incompatible types. Found: 'long', required: 'double'">CONST_L</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'double'">1f</error> -> System.out.println(1); //error
      case <error descr="Incompatible types. Found: 'float', required: 'double'">CONST_F</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'double'">1f</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }

    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'double'">1f + 2f</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'double'">CONST_F_NAN</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case 1. -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case Double.NaN -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case 7 * 1. -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case CONST_D -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Constant expression required">CONST_DOUBLE</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
  }

  public static void test_Double(Double l) {
    switch (l) {
      case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Double'">'1'</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">1</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Double'">1L</error> -> System.out.println(1); //error
      case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Double'">CONST_L</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">1f</error> -> System.out.println(1); //error
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">CONST_F</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">1f</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }

    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">1f + 2f</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">CONST_F_NAN</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
    switch (l) {
      case 1. -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case Double.NaN -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case 7 * 1. -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case CONST_D -> System.out.println(1);
      default -> System.out.println(l);
    }
    switch (l) {
      case <error descr="Constant expression required">CONST_DOUBLE</error> -> System.out.println(1); //error
      default -> System.out.println(l);
    }
  }


  public static void test_float_special(float f) {
    final float P_I = Float.POSITIVE_INFINITY;
    final float NaN = Float.NaN;
    final float N_I = Float.NEGATIVE_INFINITY;

    switch (f) {
      case P_I ->   System.out.println(1);
      case N_I ->   System.out.println(2);
      case NaN ->   System.out.println(3);
      case 0.0f ->   System.out.println(4);
      case -0.0f -> System.out.println(5);
      default -> System.out.println(6);
    }
  }

  public static void test_float_special_1() {
    float f = 1.0f / 0.0f;
    final float P_I = Float.POSITIVE_INFINITY;
    final float NaN = Float.NaN;
    final float N_I = Float.NEGATIVE_INFINITY;

    switch (f) {
      case P_I ->   System.out.println(1);
      case N_I ->   System.out.println(2);
      case NaN ->   System.out.println(3);
      case 0.0f ->   System.out.println(4);
      case -0.0f -> System.out.println(5);
      default -> System.out.println(6);
    }
  }
}
