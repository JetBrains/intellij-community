package inspection;

public class MathClampMigration {
  
  private static final int FIXED_INT_VALUE = 5;
  private static final long FIXED_LONG_VALUE = 5L;
  private static final float FIXED_FLOAT_VALUE = 5.0F;
  private static final double FIXED_DOUBLE_VALUE = 5.0D;
  
  /* ints */
  
  void intConstantTest(int input, int secondInput) {
    // Basic cases
    <warning descr="Can be replaced with 'Math.clamp()'">Math<caret>.max(5, Math.min(10, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, 10), 5)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, 5), 10)</warning>;
    
    // Cases where the variable to clamp is (more or less) ambiguous 
    int beep = 10;
    int boop = 5;
    // local variables (constant-like)
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(boop, Math.min(beep, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, beep), boop)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, boop), beep)</warning>;
    // static variables (constant-like ?) 
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(FIXED_INT_VALUE, Math.min(beep, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, beep), FIXED_INT_VALUE)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, FIXED_INT_VALUE), beep)</warning>;

    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(/*1*/beep/*2*/, Math.max(/*3*/input/*4*/, /*5*/FIXED_INT_VALUE/*6*/))</warning>;

    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(((/* Hewwo */beep)), ((Math.max(input, Integer.MIN_VALUE))))</warning>;

    // Nested cases
    int blorp = 50;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(boop, Math.min(beep, Math.min(input, blorp)))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(Math.min(input, blorp), beep), boop)</warning>;
    int blarp = 2;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(Math.max(input, boop), blarp), boop)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(boop, Math.max(blarp, Math.max(input, boop)))</warning>;
    
    // Case where the assignement make it less ambiguous
    if (secondInput > boop) {
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(secondInput, Math.max(input, boop))</warning>;
    }
  }

  void intRelationTest(int input, int a, int b) {
    if (a >=10 && b == 10) {
      // Should work
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(b, input), a)</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(a, Math.max(b, input))</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, b), a)</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(a, Math.min(input, b))</warning>;

      // Should not work
      Math.max(b, Math.min(a, input));
      Math.min(Math.max(a, input), b);
      return;
    }
    
    if (a <= 10 && b == 10) {
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(a, Math.min(b, input))</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(b, input), a)</warning>;
    }
    // Cannot know from pure variable relation
    if (a < b) {
      input = Math.max(a, Math.min(input, b));
      return;
    }
  }

  int deepInTheSauce(int a, int b, int c, int d, int e, int f, int g, int h) {
    // should not be seen outside of brave mode
    a = Math.max(Math.min(Math.min(a,Math.min(f,g)), b), Math.min(c, Math.min(d, e)));
    return a;
  }
  
  
  /* Floats */

  void floatConstantTest(float input, float secondInput) {
    // Basic cases
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(5, Math.min(10, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, 10), 5)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, 5), 10)</warning>;

    // Cases where the variable to clamp is (more or less) ambiguous 
    float beep = 10f;
    float boop = 5f;
    // local variables (constant-like)
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(boop, Math.min(beep, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, beep), boop)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, boop), beep)</warning>;
    // static variables (constant-like ?) 
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(FIXED_FLOAT_VALUE, Math.min(beep, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, beep), FIXED_FLOAT_VALUE)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, FIXED_FLOAT_VALUE), beep)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(beep, Math.max(input, FIXED_FLOAT_VALUE))</warning>;

    // Nested cases
    float blorp = 50f;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(boop, Math.min(beep, Math.min(input, blorp)))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(Math.min(input, blorp), beep), boop)</warning>;
    float blarp = 2f;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(Math.max(input, boop), blarp), boop)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(boop, Math.max(blarp, Math.max(input, boop)))</warning>;

    // Case where the assignement make it less ambiguous
    if (secondInput > boop) {
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(secondInput, Math.max(input, boop))</warning>;
    }
  }

  void floatRelationTest(float input, float a, float b) {
    if (a >=10 && b == 10) {
      // Should work
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(b, input), a)</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(a, Math.max(b, input))</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, b), a)</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(a, Math.min(input, b))</warning>;

      // Should not work
      Math.max(b, Math.min(a, input));
      Math.min(Math.max(a, input), b);
      return;
    }

    if (a <= 10 && b == 10) {
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(a, Math.min(b, input))</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(b, input), a)</warning>;
    }
    // Cannot know from pure variable relation
    if (a < b) {
      input = Math.max(a, Math.min(input, b));
      return;
    }
  }

  /* longs */

  void longConstantTest(long input, long secondInput) {
    // Basic cases
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(5L, Math.min(10L, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, 10L), 5L)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, 5L), 10L)</warning>;

    // Cases where the variable to clamp is (more or less) ambiguous
    long beep = 10L;
    long boop = 5L;
    // local variables (constant-like)
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(boop, Math.min(beep, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, beep), boop)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, boop), beep)</warning>;
    // static variables (constant-like ?)
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(FIXED_LONG_VALUE, Math.min(beep, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, beep), FIXED_LONG_VALUE)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, FIXED_LONG_VALUE), beep)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(beep, Math.max(input, FIXED_LONG_VALUE))</warning>;

    // Nested cases
    long blorp = 50L;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(boop, Math.min(beep, Math.min(input, blorp)))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(Math.min(input, blorp), beep), boop)</warning>;
    long blarp = 2L;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(Math.max(input, boop), blarp), boop)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(boop, Math.max(blarp, Math.max(input, boop)))</warning>;

    // Case where the assignement make it less ambiguous
    if (secondInput > boop) {
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(secondInput, Math.max(input, boop))</warning>;
    }
  }

  void longRelationTest(long input, long a, long b) {
    if (a >= 10L && b == 10L) {
      // Should work
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(b, input), a)</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(a, Math.max(b, input))</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, b), a)</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(a, Math.min(input, b))</warning>;

      // Should not work
      Math.max(b, Math.min(a, input));
      Math.min(Math.max(a, input), b);
      return;
    }

    if (a <= 10L && b == 10L) {
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(a, Math.min(b, input))</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(b, input), a)</warning>;
    }
    // Cannot know from pure variable relation
    if (a < b) {
      input = Math.max(a, Math.min(input, b));
      return;
    }
  }

  /* Doubles */

  void doubleConstantTest(double input, double secondInput) {
    // Basic cases
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(5.0, Math.min(10.0, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, 10.0), 5.0)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, 5.0), 10.0)</warning>;

    // Cases where the variable to clamp is (more or less) ambiguous
    double beep = 10.0;
    double boop = 5.0;
    // local variables (constant-like)
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(boop, Math.min(beep, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, beep), boop)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, boop), beep)</warning>;
    // static variables (constant-like ?)
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(FIXED_DOUBLE_VALUE, Math.min(beep, input))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, beep), FIXED_DOUBLE_VALUE)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(input, FIXED_DOUBLE_VALUE), beep)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(beep, Math.max(input, FIXED_DOUBLE_VALUE))</warning>;

    // Nested cases
    double blorp = 50.0;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(boop, Math.min(beep, Math.min(input, blorp)))</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(Math.min(input, blorp), beep), boop)</warning>;
    double blarp = 2.0;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(Math.max(input, boop), blarp), boop)</warning>;
    <warning descr="Can be replaced with 'Math.clamp()'">Math.min(boop, Math.max(blarp, Math.max(input, boop)))</warning>;

    // Case where the assignement make it less ambiguous
    if (secondInput > boop) {
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(secondInput, Math.max(input, boop))</warning>;
    }
  }

  void doubleRelationTest(double input, double a, double b) {
    if (a >= 10.0 && b == 10.0) {
      // Should work
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(Math.max(b, input), a)</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.min(a, Math.max(b, input))</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(input, b), a)</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(a, Math.min(input, b))</warning>;

      // Should not work
      Math.max(b, Math.min(a, input));
      Math.min(Math.max(a, input), b);
      return;
    }

    if (a <= 10.0 && b == 10.0) {
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(a, Math.min(b, input))</warning>;
      input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(Math.min(b, input), a)</warning>;
    }
    // Cannot know from pure variable relation
    if (a < b) {
      input = Math.max(a, Math.min(input, b));
      return;
    }
  }

}
