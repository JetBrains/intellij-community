/// - a module [java.base/]
/// - a package [java.util]
/// - a class [String]
/// - a field [String#CASE_INSENSITIVE_ORDER]
/// - a method [String#chars()]
///
///
/// - [the `java.base` module][java.base/]
/// - [the `java.util` package][java.util]
/// - [a class][String]
/// - [a field][String#CASE_INSENSITIVE_ORDER]
/// - [a method][String#chars()]
///
/// ---
///
/// | Latin | Greek |
/// |-------|-------|
/// | a     | alpha |
/// | b     | beta  |
/// | c     | gamma |
///
/// ---
///
/// ```fakeLanguageBecauseCssWillBeColoredDependingOnWhetherYouHaveThePluginEnabledMakingTheCIFail
/// p { color: red }
/// ```
///
/// ---
///
/// This is an example ...
///
/// ... of a 3-line comment containing a blank line.
///
/// ---
///
/// Here is an example:
///
/// ```
/// /** Hello World! */
/// public class HelloWorld {
///     public static void main(String... args) {
///         System.out.println("Hello World!"); // the traditional example
///     }
/// }
/// ```
///
/// ---
///
/// The following code span contains literal text, and not a JavaDoc tag:
/// `{@inheritDoc}`
///
/// In the following indented code block, `@Override` is an annotation,
/// and not a JavaDoc tag: (NOT SUPPORTED IN IDEA AS OF 2026.1)
///
///     `@Override`
///     public void m() ...
///
/// Likewise, in the following fenced code block, `@Override` is an annotation,
/// and not a JavaDoc tag:
///
/// ```
/// @Override
/// public void m() ...
/// ```
///
/// ---
///
/// For more information on comments, see {@jls 3.7 Comments}.
///
/// @implSpec
/// This implementation does nothing.
class MarkdownJepExample {}