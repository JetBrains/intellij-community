import java.lang.String;

/**
 * <warning descr="Class/method reference, quoted text, or HTML link are expected after @see tag">@see</warning>
 * @see <warning descr="Class/method reference, quoted text, or HTML link are expected after @see tag">{@link C}</warning> clarification
 *
 * @see C a correct one
 * @see C#f
 * @see java.base/
 * @see java.base/java.lang.String#length()
 * @see "The Java Language Specification, Java SE 8 Edition"
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/index.html">The Java Language Specification, Java SE 8 Edition</a>
 */
class C {
  int f;
}