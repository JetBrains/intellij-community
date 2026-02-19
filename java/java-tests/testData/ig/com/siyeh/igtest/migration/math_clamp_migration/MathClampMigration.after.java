package inspection;

public class MathClampMigration {
  
  private static final int FIXED_INT_VALUE = 5;
  private static final long FIXED_LONG_VALUE = 5L;
  private static final float FIXED_FLOAT_VALUE = 5.0F;
  private static final double FIXED_DOUBLE_VALUE = 5.0D;
  
  /* ints */
  
  void intConstantTest(int input, int secondInput) {
    // Basic cases
    Math.clamp(input, 5, 10);
    Math.clamp(input, 5, 10);
    Math.clamp(input, 5, 10);
    
    // Cases where the variable to clamp is (more or less) ambiguous 
    int beep = 10;
    int boop = 5;
    // local variables (constant-like)
    Math.clamp(input, boop, beep);
    Math.clamp(input, boop, beep);
    Math.clamp(input, boop, beep);
    // static variables (constant-like ?) 
    Math.clamp(input, FIXED_INT_VALUE, beep);
    Math.clamp(input, FIXED_INT_VALUE, beep);
    Math.clamp(input, FIXED_INT_VALUE, beep);

    Math.clamp(/*3*/input/*4*/, /*5*/FIXED_INT_VALUE/*6*/,/*1*/beep/*2*/);

    Math.clamp(input, Integer.MIN_VALUE,/* Hewwo */beep);

    // Nested cases
    int blorp = 50;
    Math.clamp(input, boop, Math.min(beep, blorp));
    Math.clamp(input, boop, Math.min(blorp, beep));
    int blarp = 2;
    Math.clamp(input, Math.max(boop, blarp), boop);
    Math.clamp(input, boop, Math.max(blarp, boop));
    
    // Case where the assignement make it less ambiguous
    if (secondInput > boop) {
      input = Math.clamp(input, boop, secondInput);
    }
  }

  void intRelationTest(int input, int a, int b) {
    if (a >=10 && b == 10) {
      // Should work
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);

      // Should not work
      Math.max(b, Math.min(a, input));
      Math.min(Math.max(a, input), b);
      return;
    }
    
    if (a <= 10 && b == 10) {
      input = Math.clamp(input, a, b);
      input = Math.clamp(input, a, b);
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
    Math.clamp(input, 5, 10);
    Math.clamp(input, 5, 10);
    Math.clamp(input, 5, 10);

    // Cases where the variable to clamp is (more or less) ambiguous 
    float beep = 10f;
    float boop = 5f;
    // local variables (constant-like)
    Math.clamp(input, boop, beep);
    Math.clamp(input, boop, beep);
    Math.clamp(input, boop, beep);
    // static variables (constant-like ?) 
    Math.clamp(input, FIXED_FLOAT_VALUE, beep);
    Math.clamp(input, FIXED_FLOAT_VALUE, beep);
    Math.clamp(input, FIXED_FLOAT_VALUE, beep);
    Math.clamp(input, FIXED_FLOAT_VALUE, beep);

    // Nested cases
    float blorp = 50f;
    Math.clamp(input, boop, Math.min(beep, blorp));
    Math.clamp(input, boop, Math.min(blorp, beep));
    float blarp = 2f;
    Math.clamp(input, Math.max(boop, blarp), boop);
    Math.clamp(input, boop, Math.max(blarp, boop));

    // Case where the assignement make it less ambiguous
    if (secondInput > boop) {
      input = Math.clamp(input, boop, secondInput);
    }
  }

  void floatRelationTest(float input, float a, float b) {
    if (a >=10 && b == 10) {
      // Should work
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);

      // Should not work
      Math.max(b, Math.min(a, input));
      Math.min(Math.max(a, input), b);
      return;
    }

    if (a <= 10 && b == 10) {
      input = Math.clamp(input, a, b);
      input = Math.clamp(input, a, b);
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
    Math.clamp(input, 5L, 10L);
    Math.clamp(input, 5L, 10L);
    Math.clamp(input, 5L, 10L);

    // Cases where the variable to clamp is (more or less) ambiguous
    long beep = 10L;
    long boop = 5L;
    // local variables (constant-like)
    Math.clamp(input, boop, beep);
    Math.clamp(input, boop, beep);
    Math.clamp(input, boop, beep);
    // static variables (constant-like ?)
    Math.clamp(input, FIXED_LONG_VALUE, beep);
    Math.clamp(input, FIXED_LONG_VALUE, beep);
    Math.clamp(input, FIXED_LONG_VALUE, beep);
    Math.clamp(input, FIXED_LONG_VALUE, beep);

    // Nested cases
    long blorp = 50L;
    Math.clamp(input, boop, Math.min(beep, blorp));
    Math.clamp(input, boop, Math.min(blorp, beep));
    long blarp = 2L;
    Math.clamp(input, Math.max(boop, blarp), boop);
    Math.clamp(input, boop, Math.max(blarp, boop));

    // Case where the assignement make it less ambiguous
    if (secondInput > boop) {
      input = Math.clamp(input, boop, secondInput);
    }
  }

  void longRelationTest(long input, long a, long b) {
    if (a >= 10L && b == 10L) {
      // Should work
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);

      // Should not work
      Math.max(b, Math.min(a, input));
      Math.min(Math.max(a, input), b);
      return;
    }

    if (a <= 10L && b == 10L) {
      input = Math.clamp(input, a, b);
      input = Math.clamp(input, a, b);
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
    Math.clamp(input, 5.0, 10.0);
    Math.clamp(input, 5.0, 10.0);
    Math.clamp(input, 5.0, 10.0);

    // Cases where the variable to clamp is (more or less) ambiguous
    double beep = 10.0;
    double boop = 5.0;
    // local variables (constant-like)
    Math.clamp(input, boop, beep);
    Math.clamp(input, boop, beep);
    Math.clamp(input, boop, beep);
    // static variables (constant-like ?)
    Math.clamp(input, FIXED_DOUBLE_VALUE, beep);
    Math.clamp(input, FIXED_DOUBLE_VALUE, beep);
    Math.clamp(input, FIXED_DOUBLE_VALUE, beep);
    Math.clamp(input, FIXED_DOUBLE_VALUE, beep);

    // Nested cases
    double blorp = 50.0;
    Math.clamp(input, boop, Math.min(beep, blorp));
    Math.clamp(input, boop, Math.min(blorp, beep));
    double blarp = 2.0;
    Math.clamp(input, Math.max(boop, blarp), boop);
    Math.clamp(input, boop, Math.max(blarp, boop));

    // Case where the assignement make it less ambiguous
    if (secondInput > boop) {
      input = Math.clamp(input, boop, secondInput);
    }
  }

  void doubleRelationTest(double input, double a, double b) {
    if (a >= 10.0 && b == 10.0) {
      // Should work
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);
      input = Math.clamp(input, b, a);

      // Should not work
      Math.max(b, Math.min(a, input));
      Math.min(Math.max(a, input), b);
      return;
    }

    if (a <= 10.0 && b == 10.0) {
      input = Math.clamp(input, a, b);
      input = Math.clamp(input, a, b);
    }
    // Cannot know from pure variable relation
    if (a < b) {
      input = Math.max(a, Math.min(input, b));
      return;
    }
  }

}
