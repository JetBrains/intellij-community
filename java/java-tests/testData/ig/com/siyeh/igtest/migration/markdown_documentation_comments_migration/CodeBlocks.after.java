
/// Provides type-safe access to data.
///
/// Implementation note: Please don't create too many instances of this class, because internal maps could overflow.
/// Instead, store the Key instance in a private static field and use it from outside.
/// For example,
///
/// ```
/// class KeyUsage {
///     private static final Key<String> MY_NAME_KEY = Key.create("my name");
///     String getName() { return getData(MY_NAME_KEY); }
///   }
/// ```
///
/// @author max
/// @author Konstantin Bulenkov
/// @see KeyWithDefaultValue
class CodeBlocks {}


/// An abstract class to be used in the cases where we need `Runnable`
/// to perform  some actions on an appendable set of data.
/// The set of data might be appended after the `Runnable` is
/// sent for the execution. Usually such `Runnables` are sent to
/// the EDT.
///
/// Usage example:
///
/// Say we want to implement JLabel.setText(String text) which sends
/// `text` string to the JLabel.setTextImpl(String text) on the EDT.
/// In the event JLabel.setText is called rapidly many times off the EDT
/// we will get many updates on the EDT but only the last one is important.
/// (Every next updates overrides the previous one.)
/// We might want to implement this `setText` in a way that only
/// the last update is delivered.
///
/// Here is how one can do this using `AccumulativeRunnable`:
///
/// <pre>
/// {@code AccumulativeRunnable<String> doSetTextImpl =
///   new  AccumulativeRunnable<String>()} {
///    {@literal @Override}
///    {@code protected void run(List<String> args)} {
///         //set to the last string being passed
///         setTextImpl(args.get(args.size() - 1));
///     }
/// }
/// void setText(String text) {
///     //add text and send for the execution if needed.
///     doSetTextImpl.add(text);
/// }
/// </pre>
///
/// Say we want to implement addDirtyRegion(Rectangle rect)
/// which sends this region to the
/// `handleDirtyRegions(List<Rect> regions)` on the EDT.
/// addDirtyRegions better be accumulated before handling on the EDT.
///
/// Here is how it can be implemented using AccumulativeRunnable:
///
/// <pre>
/// {@code AccumulativeRunnable<Rectangle> doHandleDirtyRegions =}
///    {@code new AccumulativeRunnable<Rectangle>()} {
///        {@literal @Override}
///        {@code protected void run(List<Rectangle> args)} {
///             handleDirtyRegions(args);
///         }
///     };
///  void addDirtyRegion(Rectangle rect) {
///      doHandleDirtyRegions.add(rect);
///  }
/// </pre>
///
/// @author Igor Kushnirskiy
///
/// @param <A> the type this `Runnable` accumulates
///
/// @since 1.6
interface GenericInterface<A> {}

class File {
  /// Finds the definition expression of a given variable or reference expression within the provided context.
  ///
  /// Consider the following code:
  ///
  /// ```
  /// class A {
  ///   void sample() {
  ///     String[] names = {"charlie", "joe"};
  ///     Assert.assertEquals(Arrays.asList(names), List.of("charlie", "joe"));
  ///   }
  /// }
  /// ```
  /// ```
  /// Pattern p = Pattern.compile("cat");
  /// Matcher m = p.matcher("one cat two cats in the yard");
  /// StringBuffer sb = new StringBuffer();
  /// while (m.find()) {
  ///     m.appendReplacement(sb, "dog");
  /// }
  /// m.appendTail(sb);
  /// System.out.println(sb.toString());
  /// ```
  /// ```
  /// Single line
  /// ```
  /// ```
  ///     Single line but tags at different lines
  /// ```
  /// ```
  /// Single line but end tags at different lines
  /// ```
  /// ```
  ///    Single line but start tags at different lines
  /// ```
  ///
  /// The usage of `names` in a call to `assertEquals` is a [PsiReferenceExpression].
  ///
  /// When this method is called on that [PsiReferenceExpression], it returns [PsiExpression] that defined `names`.
  /// In our example, it will be an instance of [PsiArrayInitializerExpression].
  ///
  /// @param referenceExpression the reference expression whose definition is to be found.
  /// @param variable the optional variable that is associated with the reference expression.
  /// @return the definition expression if found, otherwise null.
  public void find() {}
}

/// Returns a `String` object representing this `UUID`.
///
/// The UUID string representation is as described by this BNF:
///
/// ```
/// UUID                   = <time_low> "-" <time_mid> "-"
///                          <time_high_and_version> "-"
///                          <variant_and_sequence> "-"
///                          <node>
/// time_low               = 4*<hexOctet>
/// time_mid               = 2*<hexOctet>
/// time_high_and_version  = 2*<hexOctet>
/// variant_and_sequence   = 2*<hexOctet>
/// node                   = 6*<hexOctet>
/// hexOctet               = <hexDigit><hexDigit>
/// hexDigit               =
///       "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
///       | "a" | "b" | "c" | "d" | "e" | "f"
///       | "A" | "B" | "C" | "D" | "E" | "F"
/// ```
interface UUIDFromStandarLibrary<A> {}