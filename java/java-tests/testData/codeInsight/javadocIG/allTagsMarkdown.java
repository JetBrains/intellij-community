import java.lang.Math;

class X {

  /// {@summary Sample method demonstrating all Javadoc tags} <br/>
  /// {@return calculated result} <br/>
  /// `Math.E` <br/>
  /// {@literal <T>} <br/>
  /// [Math#E] <br/>
  /// {@linkplain Math#E Euler constant} <br/>
  /// {@index calculation} <br/>
  /// {@systemProperty java.version} <br/>
  /// {@value Math#E}
  /// {@snippet : return a + b;}
  /// @param <T> type parameter
  /// @param a first parameter
  /// @param b second parameter
  /// @return sum result
  /// @throws ArithmeticException overflow
  /// @exception IllegalArgumentException invalid input
  /// @see Math#E
  /// @since 1.0
  /// @author John Doe
  /// @version 2.0
  /// @deprecated use [Math#addExact]
  /// @serial include
  /// @serialData writes int, double
  /// @serialField value double field
  /// @spec <a href="https://example.com">link</a> spec
  /// @provides Math
  /// @uses Math
  /// @hidden
  public <T extends Number> double calculate<caret>Md(T a, T b) {
    return a.doubleValue() + b.doubleValue();
  }
}