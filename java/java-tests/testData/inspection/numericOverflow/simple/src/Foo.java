// overflows in const expressions


class c {
 // @*#$& ! references do not work in JRE
 static final long LONG_MIN_VALUE = 0x8000000000000000L;
 static final long LONG_MAX_VALUE = 0x7fffffffffffffffL;
 static final int INTEGER_MIN_VALUE = 0x80000000;
 static final int INTEGER_MAX_VALUE = 0x7fffffff;
 static final float FLOAT_MIN_VALUE = 1.4e-45f;
 static final float FLOAT_MAX_VALUE = 3.4028235e+38f;
 static final double DOUBLE_MIN_VALUE = 4.9e-324;
 static final double DOUBLE_MAX_VALUE = 1.7976931348623157e+308;

 void f() {
   float f1 = 1.0f / 0.0f;
   f1 = FLOAT_MAX_VALUE + FLOAT_MAX_VALUE;
   f1 = FLOAT_MAX_VALUE * 2;
   f1 = 2 / FLOAT_MIN_VALUE;
   f1 = FLOAT_MIN_VALUE + 1;
   f1 = - FLOAT_MIN_VALUE;
   f1 = -FLOAT_MAX_VALUE;
   System.out.println(f1);

   double d1 = DOUBLE_MAX_VALUE - -DOUBLE_MAX_VALUE;
   d1 = DOUBLE_MAX_VALUE + 1;
   d1 = 2 * DOUBLE_MAX_VALUE;
   d1 = 2 / DOUBLE_MIN_VALUE;
   d1 = 2 / 0.0d;
   d1 = 2 / 0.0;
   System.out.println(d1);


   int i1 = INTEGER_MAX_VALUE + 1;
   i1 = INTEGER_MAX_VALUE - 1 + 2;
   i1 = INTEGER_MAX_VALUE - INTEGER_MIN_VALUE;
   i1 = -INTEGER_MIN_VALUE;
   i1 = INTEGER_MIN_VALUE - 1;
   i1 = INTEGER_MIN_VALUE - INTEGER_MAX_VALUE;
   i1 = INTEGER_MIN_VALUE + INTEGER_MAX_VALUE;
   i1 = - INTEGER_MAX_VALUE;
   i1 = - -INTEGER_MAX_VALUE;
   i1 = INTEGER_MIN_VALUE * -1;
   i1 = INTEGER_MIN_VALUE * 2;
   i1 = INTEGER_MAX_VALUE * -2;
   i1 = INTEGER_MAX_VALUE * -1;
   i1 = 2 / 0;
   i1 = INTEGER_MIN_VALUE / -1;
   System.out.println(i1);

   long l1 = LONG_MAX_VALUE + 1;
   l1 = LONG_MAX_VALUE - 1 + 2;
   l1 = LONG_MAX_VALUE - LONG_MIN_VALUE;
   l1 = -LONG_MIN_VALUE;
   l1 = LONG_MIN_VALUE - 1;
   l1 = LONG_MIN_VALUE - LONG_MAX_VALUE;
   l1 = LONG_MIN_VALUE + LONG_MAX_VALUE;
   l1 = - LONG_MAX_VALUE;
   l1 = - -LONG_MAX_VALUE;
   l1 = -INTEGER_MIN_VALUE;
   l1 =  -1L + INTEGER_MIN_VALUE;
   l1 = LONG_MIN_VALUE * INTEGER_MIN_VALUE;
   l1 = LONG_MIN_VALUE * -1;
   l1 = LONG_MIN_VALUE * 2;
   l1 = LONG_MAX_VALUE * -2;
   l1 = INTEGER_MIN_VALUE * -1L;
   l1 = 2 / 0L;
   l1 = 2 % 0L;
   l1 = LONG_MIN_VALUE / -1;

   l1 = 30 * 24 * 60 * 60 * 1000; 
   l1 = 30000000 * 243232323 * (LONG_MAX_VALUE +3) / 5;

   System.out.println(l1);


   
   }
   
    private static final long MILLIS_PER_DAY = 24 * 3600 * 1000;
    private static final long _7DAYS = 7 * MILLIS_PER_DAY;
    private static final long _30DAYS = 30 * MILLIS_PER_DAY;
    private static final long _1000DAYS = 1000 * MILLIS_PER_DAY;
    {
       System.out.println(_1000DAYS + _30DAYS + _7DAYS);
    }
    int iii = 2 % 0;
}
