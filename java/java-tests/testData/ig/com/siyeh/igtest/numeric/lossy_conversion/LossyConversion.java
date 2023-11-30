package com.siyeh.igtest.numeric.lossy_conversion;

public class LossyConversion {

  public void testChar() {
    char c = 1;
    c += 1;

    //not highlighted - within range
    c += 60_000;
    c -= 60_000;
    c *= 60_000;
    c /= 60_000;

    c += <warning descr="Implicit cast from 'int' to 'char' in compound assignment can be lossy">60_000 * 2</warning>;
    c -= <warning descr="Implicit cast from 'int' to 'char' in compound assignment can be lossy">60_000 * 2</warning>;
    c *= <warning descr="Implicit cast from 'int' to 'char' in compound assignment can be lossy">60_000 * 2</warning>;
    c /= <warning descr="Implicit cast from 'int' to 'char' in compound assignment can be lossy">60_000 * 2</warning>;
    c |= <warning descr="Implicit cast from 'long' to 'char' in compound assignment can be lossy">1L</warning>;
    c &= <warning descr="Implicit cast from 'long' to 'char' in compound assignment can be lossy">1L</warning>;
    c ^= <warning descr="Implicit cast from 'long' to 'char' in compound assignment can be lossy">1L</warning>;
    c %= <warning descr="Implicit cast from 'long' to 'char' in compound assignment can be lossy">1L</warning>;
  }

  public void test() {
    byte b = 1;
    b += <warning descr="Implicit cast from 'double' to 'byte' in compound assignment can be lossy">1.1</warning>;
    b -= <warning descr="Implicit cast from 'double' to 'byte' in compound assignment can be lossy">2.2</warning>;
    b *= <warning descr="Implicit cast from 'double' to 'byte' in compound assignment can be lossy">3.3</warning>;
    b /= <warning descr="Implicit cast from 'double' to 'byte' in compound assignment can be lossy">4.4</warning>;

    //not highlighted - within range
    b += 120;
    b -= 120;
    b *= 120;
    b /= 120;

    b += <warning descr="Implicit cast from 'int' to 'byte' in compound assignment can be lossy">130</warning>;
    b -= <warning descr="Implicit cast from 'int' to 'byte' in compound assignment can be lossy">130</warning>;
    b *= <warning descr="Implicit cast from 'int' to 'byte' in compound assignment can be lossy">130</warning>;
    b /= <warning descr="Implicit cast from 'int' to 'byte' in compound assignment can be lossy">130</warning>;
    b |= <warning descr="Implicit cast from 'long' to 'byte' in compound assignment can be lossy">1L</warning>;
    b &= <warning descr="Implicit cast from 'long' to 'byte' in compound assignment can be lossy">1L</warning>;
    b ^= <warning descr="Implicit cast from 'long' to 'byte' in compound assignment can be lossy">1L</warning>;
    b %= <warning descr="Implicit cast from 'long' to 'byte' in compound assignment can be lossy">1L</warning>;


    short s = 1;
    s += <warning descr="Implicit cast from 'double' to 'short' in compound assignment can be lossy">1.1</warning>;
    s -= <warning descr="Implicit cast from 'double' to 'short' in compound assignment can be lossy">2.2</warning>;
    s *= <warning descr="Implicit cast from 'double' to 'short' in compound assignment can be lossy">3.3</warning>;
    s /= <warning descr="Implicit cast from 'double' to 'short' in compound assignment can be lossy">4.4</warning>;

    //not highlighted - within range
    s += 30_000;
    s -= 30_000;
    s *= 30_000;
    s /= 30_000;

    s += <warning descr="Implicit cast from 'int' to 'short' in compound assignment can be lossy">60_000</warning>;
    s -= <warning descr="Implicit cast from 'int' to 'short' in compound assignment can be lossy">60_000</warning>;
    s *= <warning descr="Implicit cast from 'int' to 'short' in compound assignment can be lossy">60_000</warning>;
    s /= <warning descr="Implicit cast from 'int' to 'short' in compound assignment can be lossy">60_000</warning>;

    int i = 1;
    i += <warning descr="Implicit cast from 'double' to 'int' in compound assignment can be lossy">1.1</warning>;
    i -= <warning descr="Implicit cast from 'double' to 'int' in compound assignment can be lossy">2.2</warning>;
    i *= <warning descr="Implicit cast from 'double' to 'int' in compound assignment can be lossy">3.3</warning>;
    i /= <warning descr="Implicit cast from 'double' to 'int' in compound assignment can be lossy">4.4</warning>;
    //not highlighted
    i += 60_000;
    i -= 60_000;
    i *= 60_000;
    i /= 60_000;

    long l = 1;
    l += <warning descr="Implicit cast from 'double' to 'long' in compound assignment can be lossy">1.1</warning>;
    l -= <warning descr="Implicit cast from 'double' to 'long' in compound assignment can be lossy">2.2</warning>;
    l *= <warning descr="Implicit cast from 'double' to 'long' in compound assignment can be lossy">3.3</warning>;
    l /= <warning descr="Implicit cast from 'double' to 'long' in compound assignment can be lossy">4.4</warning>;
    //not highlighted
    l += 60_000 * 2;
    l -= 60_000 * 2;
    l *= 60_000 * 2;
    l /= 60_000 * 2;
    //not highlighted
    l |= 60_000L;
    l &= 60_000L;
    l ^= 60_000L;
    l %= 60_000L;

    float f = 1;
    f += <warning descr="Implicit cast from 'double' to 'float' in compound assignment can be lossy">1.1</warning>;
    f -= <warning descr="Implicit cast from 'double' to 'float' in compound assignment can be lossy">2.2</warning>;
    f *= <warning descr="Implicit cast from 'double' to 'float' in compound assignment can be lossy">3.3</warning>;
    f /= <warning descr="Implicit cast from 'double' to 'float' in compound assignment can be lossy">4.4</warning>;

    double d = 1.0;
    //not highlighted
    d += 1.1;
    d -= 2.2;
    d *= 3.3;
    d /= 4.4;
    //not highlighted
    b += b;
    b -= b;
    b *= b;
    b /= b;
    //not highlighted
    b |= b;
    b &= b;
    b ^= b;
    b %= b;

    b += <warning descr="Implicit cast from 'short' to 'byte' in compound assignment can be lossy">s</warning>;
    b -= <warning descr="Implicit cast from 'short' to 'byte' in compound assignment can be lossy">s</warning>;
    b *= <warning descr="Implicit cast from 'short' to 'byte' in compound assignment can be lossy">s</warning>;
    b /= <warning descr="Implicit cast from 'short' to 'byte' in compound assignment can be lossy">s</warning>;
    b |= <warning descr="Implicit cast from 'short' to 'byte' in compound assignment can be lossy">s</warning>;
    b &= <warning descr="Implicit cast from 'short' to 'byte' in compound assignment can be lossy">s</warning>;
    b ^= <warning descr="Implicit cast from 'short' to 'byte' in compound assignment can be lossy">s</warning>;
    b %= <warning descr="Implicit cast from 'short' to 'byte' in compound assignment can be lossy">s</warning>;
  }

  @SuppressWarnings("lossy-conversions")
  public void withSuppression() {
    byte b = 0;
    //not highlighted
    b += <warning descr="Implicit cast from 'double' to 'byte' in compound assignment can be lossy">1.1</warning>;
    b -= <warning descr="Implicit cast from 'double' to 'byte' in compound assignment can be lossy">2.2</warning>;
    b *= <warning descr="Implicit cast from 'double' to 'byte' in compound assignment can be lossy">3.3</warning>;
    b /= <warning descr="Implicit cast from 'double' to 'byte' in compound assignment can be lossy">4.4</warning>;
  }
}
