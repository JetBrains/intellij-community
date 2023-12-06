package com.siyeh.igtest.numeric.cast_that_loses_precision;

public class CastThatLosesPrecision
{
    public CastThatLosesPrecision()
    {
    }

    public void fooBar(long l, double d, float f)
    {
        byte b;
        int i;
        char ch;

        i = (<warning descr="Cast from 'float' to 'int' may result in loss of precision">int</warning>) f;
        System.out.println("i = " + i);
        ch = (<warning descr="Cast from 'double' to 'char' may result in loss of precision">char</warning>) d;
        System.out.println("ch = " + ch);
        i = (<warning descr="Cast from 'double' to 'int' may result in loss of precision">int</warning>) d;
        System.out.println("i = " + i);
        i = (<warning descr="Cast from 'long' to 'int' may result in loss of precision">int</warning>) l;
        System.out.println("i = " + i);
        b = (<warning descr="Cast from 'long' to 'byte' may result in loss of precision">byte</warning>) l;
        System.out.println("b = " + b);

        l = (<warning descr="Cast from 'double' to 'long' may result in loss of precision">long</warning>) d;
        System.out.println("l = " + l);
        l = (<warning descr="Cast from 'float' to 'long' may result in loss of precision">long</warning>) f;
        System.out.println("l = " + l);

        d = (double) f;
        System.out.println("d = " + d);

        f = (<warning descr="Cast from 'double' to 'float' may result in loss of precision">float</warning>) d;
        System.out.println("f = " + f);
    }

    public void barFoo(long l) {
        byte b;
        int i;
        char ch;


        i = (int) 0.0f;
        System.out.println("i = " + i);
        ch = (char) 0.0;
        System.out.println("ch = " + ch);
        i = (int) 0.0;
        System.out.println("i = " + i);
        i = (int) 0L;
        System.out.println("i = " + i);
        b = (<warning descr="Cast from 'long' to 'byte' may result in loss of precision">byte</warning>) l;
        System.out.println("b = " + b);

        l = (long) 0.0;
        System.out.println("l = " + l);
        l = (long) 0.0f;
        System.out.println("l = " + l);

        final double d = (double)0.0f;
        System.out.println("d = " + d);

        final float f = (float)0.0;
        System.out.println("f = " + f);
    }


  private long aLong = 2L;
  private double d = 1.0;

  @Override
  public int hashCode() {
    int result = (int) (aLong ^ (aLong >>> 32));
    long temp = d != +0.0d ? (<warning descr="Cast from 'double' to 'int' may result in loss of precision">int</warning>) d : 0L;
    result = 31 * result + (int) (temp ^ temp >>> 32);
    return result;
  }

  void testNegativeOnly(long longNumberOfAgents) {
    if (longNumberOfAgents > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Too many agents: " + longNumberOfAgents);
    }
    int intNumberOfAgents = (<warning descr="Cast from 'long' to 'int' may result in loss of precision for negative argument">int</warning>)longNumberOfAgents;
    System.out.println(intNumberOfAgents);
  }

  void testBoundsCheck(long longNumberOfAgents) {
    if (longNumberOfAgents < 0) {
      throw new IllegalArgumentException("Negative is not allowed");
    }
    if (longNumberOfAgents > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Too many agents: " + longNumberOfAgents);
    }
    int intNumberOfAgents = (int)longNumberOfAgents;
    System.out.println(intNumberOfAgents);
  }

  void bytes() {
    byte[] bytes = { (byte) 0xb2, (byte) 0x80 };
  }
}
