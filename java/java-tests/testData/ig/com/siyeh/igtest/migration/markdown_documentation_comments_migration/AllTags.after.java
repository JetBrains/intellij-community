///<caret> {@summary Sample method demonstrating all Javadoc tags}
///
/// {@return calculated result}
///
/// `Math.E`
///
/// {@literal <T>}
///
/// [Math#E]
///
/// [Euler constant][Math#E]
///
/// {@index calculation}
///
/// {@systemProperty java.version}
///
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
/// @spec [link](https://example.com) spec
/// @provides Math
/// @uses Math
/// @hidden
@Deprecated
class AllTags {
  
  ///<caret> {@literal <T>}
  void literal() {}
  
  /// `<T>`
  void code(){}
  
  /// {@snippet :
  ///  List<String> toto = new ArrayList<>();
  /// }
  void snippet(){}
  
  
  /// <pre>
  /// &commat;Example
  ///     public void htmlCharEntity() {
  ///     }
  /// }
  /// </pre>
  void fakeTag(){}
}