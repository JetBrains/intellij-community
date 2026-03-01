import java.util.HashMap;

<warning descr="Javadoc comment can be Markdown documentation comment">/**<caret></warning>
 * test data file<br />
 * another line
 * <HR>
 * {@code System.out.println()}
 * {@link java.util.ArrayList}
 * {@link java.util.HashMap description}
 * @author Bas
 *
 * @see <a href="https://jetbrains.com">Check out our cool website !</a>
 * and our even better <a href="https://www.jetbrainsmerchandise.com">merch</a> !
 */
public class MarkdownDocumentationCommentsMigration {
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * {@return a hash code value for this object} This method is
   * supported for the benefit of hash tables such as those provided by
   * {@link HashMap}.
   * <p>
   * The general contract of {@code hashCode} is:
   * <ul>
   * <li>Whenever it is invoked on the same object more than once during
   *     an execution of a Java application, the {@code hashCode} method
   *     must consistently return the same integer, provided no information
   *     used in {@code equals} comparisons on the object is modified.
   *     This integer need not remain consistent from one execution of an
   *     application to another execution of the same application.
   * <li>If two objects are equal according to the {@link
   *     #equals(Object) equals} method, then calling the {@code
   *     hashCode} method on each of the two objects must produce the
   *     same integer result.
   * <li>It is <em>not</em> required that if two objects are unequal
   *     according to the {@link #equals(Object) equals} method, then
   *     calling the {@code hashCode} method on each of the two objects
   *     must produce distinct integer results.  However, the programmer
   *     should be aware that producing distinct integer results for
   *     unequal objects may improve the performance of hash tables.
   * </ul>
   *
   * @implSpec As far as is reasonably practical, the {@code hashCode} method defined
   * by class {@code Object} returns distinct integers for distinct objects.
   * @apiNote The {@link Objects#hash(Object...) hash} and {@link
   * Objects#hashCode(Object) hashCode} methods of {@link
   * Objects} can be used to help construct simple hash codes.
   * @see Object#equals(Object)
   * @see System#identityHashCode
   */
  @Override
  public int hashCode() {
    return super.hashCode();
  }

  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * Indicates whether some other object is "equal to" this one.
   * <p>
   * The {@code equals} method implements an equivalence relation
   * on non-null object references:
   * <ul>
   * <li>It is <i>reflexive</i>: for any non-null reference value
   *     {@code x}, {@code x.equals(x)} should return
   *     {@code true}.
   * <li>It is <i>symmetric</i>: for any non-null reference values
   *     {@code x} and {@code y}, {@code x.equals(y)}
   *     should return {@code true} if and only if
   *     {@code y.equals(x)} returns {@code true}.
   * <li>It is <i>transitive</i>: for any non-null reference values
   *     {@code x}, {@code y}, and {@code z}, if
   *     {@code x.equals(y)} returns {@code true} and
   *     {@code y.equals(z)} returns {@code true}, then
   *     {@code x.equals(z)} should return {@code true}.
   * <li>It is <i>consistent</i>: for any non-null reference values
   *     {@code x} and {@code y}, multiple invocations of
   *     {@code x.equals(y)} consistently return {@code true}
   *     or consistently return {@code false}, provided no
   *     information used in {@code equals} comparisons on the
   *     objects is modified.
   * <li>For any non-null reference value {@code x},
   *     {@code x.equals(null)} should return {@code false}.
   * </ul>
   *
   * <p>
   * An equivalence relation partitions the elements it operates on
   * into <i>equivalence classes</i>; all the members of an
   * equivalence class are equal to each other. Members of an
   * equivalence class are substitutable for each other, at least
   * for some purposes.
   *
   * @param obj the reference object with which to compare.
   * @return {@code true} if this object is the same as the obj
   * argument; {@code false} otherwise.
   * @implSpec The {@code equals} method for class {@code Object} implements
   * the most discriminating possible equivalence relation on objects;
   * that is, for any non-null reference values {@code x} and
   * {@code y}, this method returns {@code true} if and only
   * if {@code x} and {@code y} refer to the same object
   * ({@code x == y} has the value {@code true}).
   * <p>
   * In other words, under the reference equality equivalence
   * relation, each equivalence class only has a single element.
   * @apiNote It is generally necessary to override the {@link #hashCode() hashCode}
   * method whenever this method is overridden, so as to maintain the
   * general contract for the {@code hashCode} method, which states
   * that equal objects must have equal hash codes.
   * <p>The two-argument {@link Objects#equals(Object,
   * Object) Objects.equals} method implements an equivalence relation
   * on two possibly-null object references.
   * @see #hashCode()
   * @see HashMap
   */
  @Override
  public boolean equals(final Object obj) {
    return super.equals(obj);
  }

  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * Creates and returns a copy of this object.  The precise meaning
   * of "copy" may depend on the class of the object. The general
   * intent is that, for any object {@code x}, the expression:
   * <blockquote>
   * <pre>
   * x.clone() != x</pre></blockquote>
   * will be true, and that the expression:
   * <blockquote>
   * <pre>
   * x.clone().getClass() == x.getClass()</pre></blockquote>
   * will be {@code true}, but these are not absolute requirements.
   * While it is typically the case that:
   * <blockquote>
   * <pre>
   * x.clone().equals(x)</pre></blockquote>
   * will be {@code true}, this is not an absolute requirement.
   * <p>
   * By convention, the returned object should be obtained by calling
   * {@code super.clone}.  If a class and all of its superclasses (except
   * {@code Object}) obey this convention, it will be the case that
   * {@code x.clone().getClass() == x.getClass()}.
   * <p>
   * By convention, the object returned by this method should be independent
   * of this object (which is being cloned).  To achieve this independence,
   * it may be necessary to modify one or more fields of the object returned
   * by {@code super.clone} before returning it.  Typically, this means
   * copying any mutable objects that comprise the internal "deep structure"
   * of the object being cloned and replacing the references to these
   * objects with references to the copies.  If a class contains only
   * primitive fields or references to immutable objects, then it is usually
   * the case that no fields in the object returned by {@code super.clone}
   * need to be modified.
   *
   * @return a clone of this instance.
   * @throws CloneNotSupportedException if the object's class does not
   *                                    support the {@code Cloneable} interface. Subclasses
   *                                    that override the {@code clone} method can also
   *                                    throw this exception to indicate that an instance cannot
   *                                    be cloned.
   * @implSpec The method {@code clone} for class {@code Object} performs a
   * specific cloning operation. First, if the class of this object does
   * not implement the interface {@code Cloneable}, then a
   * {@code CloneNotSupportedException} is thrown. Note that all arrays
   * are considered to implement the interface {@code Cloneable} and that
   * the return type of the {@code clone} method of an array type {@code T[]}
   * is {@code T[]} where T is any reference or primitive type.
   * Otherwise, this method creates a new instance of the class of this
   * object and initializes all its fields with exactly the contents of
   * the corresponding fields of this object, as if by assignment; the
   * contents of the fields are not themselves cloned. Thus, this method
   * performs a "shallow copy" of this object, not a "deep copy" operation.
   * <p>
   * The class {@code Object} does not itself implement the interface
   * {@code Cloneable}, so calling the {@code clone} method on an object
   * whose class is {@code Object} will result in throwing an
   * exception at run time.
   * @see Cloneable
   */
  @Override
  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * {@return a string representation of the object}
   * <p>
   * Satisfying this method's contract implies a non-{@code null}
   * result must be returned.
   *
   * @apiNote In general, the
   * {@code toString} method returns a string that
   * "textually represents" this object. The result should
   * be a concise but informative representation that is easy for a
   * person to read.
   * It is recommended that all subclasses override this method.
   * The string output is not necessarily stable over time or across
   * JVM invocations.
   * @implSpec The {@code toString} method for class {@code Object}
   * returns a string consisting of the name of the class of which the
   * object is an instance, the at-sign character `{@code @}', and
   * the unsigned hexadecimal representation of the hash code of the
   * object. In other words, this method returns a string equal to the
   * value of:
   * {@snippet lang = java:
   * getClass().getName() + '@' + Integer.toHexString(hashCode())
   *}
   * The {@link Objects#toIdentityString(Object)
   * Objects.toIdentityString} method returns the string for an
   * object equal to the string that would be returned if neither
   * the {@code toString} nor {@code hashCode} methods were
   * overridden by the object's class.
   */
  @Override
  public String toString() {
    return super.toString();
  }

  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * Called by the garbage collector on an object when garbage collection
   * determines that there are no more references to the object.
   * A subclass overrides the {@code finalize} method to dispose of
   * system resources or to perform other cleanup.
   * <p>
   * <b>When running in a Java virtual machine in which finalization has been
   * disabled or removed, the garbage collector will never call
   * {@code finalize()}. In a Java virtual machine in which finalization is
   * enabled, the garbage collector might call {@code finalize} only after an
   * indefinite delay.</b>
   * <p>
   * The general contract of {@code finalize} is that it is invoked
   * if and when the Java virtual
   * machine has determined that there is no longer any
   * means by which this object can be accessed by any thread that has
   * not yet died, except as a result of an action taken by the
   * finalization of some other object or class which is ready to be
   * finalized. The {@code finalize} method may take any action, including
   * making this object available again to other threads; the usual purpose
   * of {@code finalize}, however, is to perform cleanup actions before
   * the object is irrevocably discarded. For example, the finalize method
   * for an object that represents an input/output connection might perform
   * explicit I/O transactions to break the connection before the object is
   * permanently discarded.
   * <p>
   * The {@code finalize} method of class {@code Object} performs no
   * special action; it simply returns normally. Subclasses of
   * {@code Object} may override this definition.
   * <p>
   * The Java programming language does not guarantee which thread will
   * invoke the {@code finalize} method for any given object. It is
   * guaranteed, however, that the thread that invokes finalize will not
   * be holding any user-visible synchronization locks when finalize is
   * invoked. If an uncaught exception is thrown by the finalize method,
   * the exception is ignored and finalization of that object terminates.
   * <p>
   * After the {@code finalize} method has been invoked for an object, no
   * further action is taken until the Java virtual machine has again
   * determined that there is no longer any means by which this object can
   * be accessed by any thread that has not yet died, including possible
   * actions by other objects or classes which are ready to be finalized,
   * at which point the object may be discarded.
   * <p>
   * The {@code finalize} method is never invoked more than once by a Java
   * virtual machine for any given object.
   * <p>
   * Any exception thrown by the {@code finalize} method causes
   * the finalization of this object to be halted, but is otherwise
   * ignored.
   *
   * @throws Throwable the {@code Exception} raised by this method
   * @apiNote Classes that embed non-heap resources have many options
   * for cleanup of those resources. The class must ensure that the
   * lifetime of each instance is longer than that of any resource it embeds.
   * {@link Reference#reachabilityFence} can be used to ensure that
   * objects remain reachable while resources embedded in the object are in use.
   * <p>
   * A subclass should avoid overriding the {@code finalize} method
   * unless the subclass embeds non-heap resources that must be cleaned up
   * before the instance is collected.
   * Finalizer invocations are not automatically chained, unlike constructors.
   * If a subclass overrides {@code finalize} it must invoke the superclass
   * finalizer explicitly.
   * To guard against exceptions prematurely terminating the finalize chain,
   * the subclass should use a {@code try-finally} block to ensure
   * {@code super.finalize()} is always invoked. For example,
   * {@snippet lang = "java":
   *     @Override
   *     protected void finalize() throws Throwable {
   *         try {
   *             <error descr="Unexpected token">...</error> // cleanup subclass state
   *         } finally {
   *             super.finalize();
   *         }
   *     }
   *}
   * @jls 12.6 Finalization of Class Instances
   * @see WeakReference
   * @see PhantomReference
   * @deprecated Finalization is deprecated and subject to removal in a future
   * release. The use of finalization can lead to problems with security,
   * performance, and reliability.
   * See <a href="https://openjdk.org/jeps/421">JEP 421</a> for
   * discussion and alternatives.
   * <p>
   * Subclasses that override {@code finalize} to perform cleanup should use
   * alternative cleanup mechanisms and remove the {@code finalize} method.
   * Use {@link Cleaner} and
   * {@link PhantomReference} as safer ways to release resources
   * when an object becomes unreachable. Alternatively, add a {@code close}
   * method to explicitly release resources, and implement
   * {@code AutoCloseable} to enable use of the {@code try}-with-resources
   * statement.
   * <p>
   * This method will remain in place until finalizers have been removed from
   * most existing code.
   */
  @Override
  protected void finalize() throws Throwable {
    super.finalize();
  }

}
<warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
 * Allows an action to retrieve information about the context in which it was invoked.
 * <p/>
 * <b>NOTES:</b>
 * <ul>
 * <li>Do not implement, or override platform implementations!
 * Things have got more complex since the introduction of asynchronous action update.
 * If you need to alter the provided data context or create one from a set of data
 * use {@link CustomizedDataContext} or {@link com.intellij.openapi.actionSystem.impl.SimpleDataContext} instead, even in tests.
 * These classes are async-ready, optionally support {@link com.intellij.openapi.util.UserDataHolder},
 * and run {@link com.intellij.ide.impl.dataRules.GetDataRule} rules.</li>
 * <li>Do not to confuse {@link DataProvider} with {@link DataContext}.
 * A {@link DataContext} is usually provided by the platform with {@link DataProvider}s as its building blocks.
 * For example, a node in a tree view could be a {@link DataProvider} but not a {@link DataContext}.</li>
 * </ul>
 *
 * @see DataKey
 * @see DataProvider
 * @see UiDataProvider
 * @see AnActionEvent#getDataContext()
 * @see com.intellij.ide.DataManager#getDataContext(Component)
 * @see com.intellij.openapi.actionSystem.CommonDataKeys
 * @see com.intellij.openapi.actionSystem.LangDataKeys
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys
 * @see com.intellij.openapi.actionSystem.PlatformCoreDataKeys
 * @see com.intellij.openapi.actionSystem.CustomizedDataContext
 * @see com.intellij.openapi.actionSystem.impl.SimpleDataContext
 */
interface Nothing {}
<warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
 * <h1>Ident formatter</h1>
 * <h2>What does it do?</h2>
 * <p>
 * Creates list of lookup elements with priority and help text and correct indent.
 * Indent has size of longest element to make it pretty formatted:
 * <pre>
 *   command_1         : command help text
 *   very_long_command : help goes here
 *   spam              : again here
 * </pre>
 * </p>
 * <h2>How to use it?</h2>
 * <p>
 * Create it, fill with {@link #addElement(LookupElementBuilder, String)} or {@link #addElement(LookupElementBuilder, String, int)}
 * and obtain result with {@link #getResult()}.
 * </p>
 * <h3>Priority</h3>
 * <p>If <strong>at least</strong> one element has priority, elements would be prioritized. No priority will be used otherwise</p>
 *
 * @author Ilya.Kazakevich
 */
final class LookupWithIndentsBuilder {

  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * <h1>
   * Returns a hash code value for the object. This method is
   * supported for the benefit of hash tables such as those provided by
   * {@link java.util.HashMap}.</>
   */
  public int hashCode() {
    return 1;
  }

  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * <li>one</li>
   * <li>two</li>
   */
  void x() {}
}
<warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
 * {@code test<b>}
 * {@code One Sheep,
 * 
 * Two Sheeps,
 * ...zzz}
 * 
 * {@linkplain String}
 * <br>
 * {@linkplain String I am a string, but with a label}
 * 
 * {@linkplain String#copyValueOf(char[])}
 * 
 * <p>
 * <code>Single line code block with html tag</code>
 * <p>
 * <code>
 *   
 *   Multi line 
 * 
 * code block with html
 * tag
 * </code>
 * 
 * @param <T> The <em>M</em> type.
 * @param <B> The B type.
 * @param <P> The P type.
 * @param <I> The I type.
 */
interface Foo<T, B, P, I>
{
}
<warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
 *    <prE>
 * This has some formatted text with blank lines.
 *
 * This has some formatted text with blank lines.
 *
 * This has some formatted text with blank lines.
 *
 *     An indented line.
 *
 *
 * Multiple blank lines above and more below.
 *
 *
 * </Pre>
 * end
 */
class Preformatted {

  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * <pre>{@code x}</pre>
   */
  protected void beginParsing() {}
}

class IntendedLists{
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * References:
   * <ul>
   *   <li>First item</li>
   *   <li>Second item</li>
   *   <ul>
   *     <li>First sub item</li>
   *     <li>
   *       Second sub item
   *     
   *     multi line for some reason
   *     </li>
   *   </ul>
   *   <li>Third item</li>
   * </ul>
   */
  void unorderedList() {}
  
   <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * <ol><li>first ordered element</li><li>Second ordered element</li><ol><li>Sub item</li></ol></ol> Text outside of the list.
   */
  void orderedList() {}
}

<warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
 * <a href="https://openjdk.org/jeps/467">That's JEP 467 </a>
 * <br>
 * <a href="https://openjdk.org/jeps/467"></a>
 * <br>
 * <img src='https://openjdk.org/images/duke-thinking.png'/>
 * <br>
 * <img alt="A joltik plushie" />
 * <br>
 * <a href="https://openjdk.org/jeps/467"> Personally,
 * 
 * I love writing links
 * 
 * on
 * multiple
 *  lines.
 *   
 *   </a>
 * <br>
 * Link to a type and insert {@link java.time.OffsetDateTime a line break
 * here}
 */
class OhMyLinks {}

<warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
 * I like my *asteriks* and _underscores_.
 * <p>
 * And let's not forget about my friend the `backticks` (and ~~~squigly lines~~~) !
 * <br>
 * Some fun things include:
 * ## Title like structs
 * <br>
 *   - List like structs
 *   
 *   
 *   1. Ordered list like structs 
 * 
 */
class EscapeMarkdownLikeStructure {}

class NonProcessed {
<warning descr="Javadoc comment can be Markdown documentation comment">/**</warning> 
   * Handled <i>italics</i>
   * Unhandled <i style="color:red;">italics</i>
   * 
   * <ul>
   *   <li>item one</li>
   *   <li style="color:red;">item two</li>
   * </ul>
   */
  void unknownAttributes(){}

  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * <table>
   *   <tbody>
   *   <tr>
   *     <td>col 1</td>
   *     <td>col 2</td>
   *   </tr>
   *   </tbody>
   * </table>
   */ 
  void unknownTags(){}
}

class Matcher {
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * Implements a non-terminal append-and-replace step.
   *
   * <p> This method performs the following actions: </p>
   *
   * <ol>
   *
   *   <li><p> It reads characters from the input sequence, starting at the
   *   append position, and appends them to the given string buffer.  It
   *   stops after reading the last character preceding the previous match,
   *   that is, the character at index {@link
   *   #start()}&nbsp;{@code -}&nbsp;{@code 1}.  </p></li>
   *
   *   <li><p> It appends the given replacement string to the string buffer.
   *   </p></li>
   *
   *   <li><p> It sets the append position of this matcher to the index of
   *   the last character matched, plus one, that is, to {@link #end()}.
   *   </p></li>
   *
   * </ol>
   *
   * <p> The replacement string may contain references to subsequences
   * captured during the previous match: Each occurrence of
   * <code>${</code><i>name</i><code>}</code> or {@code $}<i>g</i>
   * will be replaced by the result of evaluating the corresponding
   * {@link #group(String) group(name)} or {@link #group(int) group(g)}
   * respectively. For {@code $}<i>g</i>,
   * the first number after the {@code $} is always treated as part of
   * the group reference. Subsequent numbers are incorporated into g if
   * they would form a legal group reference. Only the numerals '0'
   * through '9' are considered as potential components of the group
   * reference. If the second group matched the string {@code "foo"}, for
   * example, then passing the replacement string {@code "$2bar"} would
   * cause {@code "foobar"} to be appended to the string buffer. A dollar
   * sign ({@code $}) may be included as a literal in the replacement
   * string by preceding it with a backslash ({@code \$}).
   *
   * <p> Note that backslashes ({@code \}) and dollar signs ({@code $}) in
   * the replacement string may cause the results to be different than if it
   * were being treated as a literal replacement string. Dollar signs may be
   * treated as references to captured subsequences as described above, and
   * backslashes are used to escape literal characters in the replacement
   * string.
   *
   * <p> This method is intended to be used in a loop together with the
   * {@link #appendTail(StringBuffer) appendTail} and {@link #find() find}
   * methods.  The following code, for example, writes {@code one dog two dogs
   * in the yard} to the standard-output stream: </p>
   *
   * <blockquote><pre>
   * Pattern p = Pattern.compile("cat");
   * Matcher m = p.matcher("one cat two cats in the yard");
   * StringBuffer sb = new StringBuffer();
   * while (m.find()) {
   *     m.appendReplacement(sb, "dog");
   * }
   * m.appendTail(sb);
   * System.out.println(sb.toString());</pre></blockquote>
   *
   * @param  sb
   *         The target string buffer
   *
   * @param  replacement
   *         The replacement string
   *
   * @return  This matcher
   *
   * @throws  IllegalStateException
   *          If no match has yet been attempted,
   *          or if the previous match operation failed
   *
   * @throws  IllegalArgumentException
   *          If the replacement string refers to a named-capturing
   *          group that does not exist in the pattern
   *
   * @throws  IndexOutOfBoundsException
   *          If the replacement string refers to a capturing group
   *          that does not exist in the pattern
   */
  void appendReplacement(){}
}

class File {
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   * Finds the definition expression of a given variable or reference expression within the provided context.
   * <p>
   * Consider the following code:
   * <pre>{@code
   * class A {
   *   void sample() {
   *     String[] names = {"charlie", "joe"};
   *     Assert.assertEquals(Arrays.asList(names), List.of("charlie", "joe"));
   *   }
   * }
   * }</pre>
   *
   * <pre><code>Single line</code></pre>
   * <pre><code>
   *     Single line but tags at different lines
   *  </code></pre>
   *
   *  <pre><code>Single line but end tags at different lines
   *  </code></pre>
   *
   *  <pre><code>
   *    Single line but start tags at different lines</code></pre>
   *
   * The usage of {@code names} in a call to {@code assertEquals} is a {@link PsiReferenceExpression}.
   * <p>
   * When this method is called on that {@link PsiReferenceExpression}, it returns {@link PsiExpression} that defined {@code names}.
   * In our example, it will be an instance of {@link PsiArrayInitializerExpression}.
   *
   * @param referenceExpression the reference expression whose definition is to be found.
   * @param variable the optional variable that is associated with the reference expression.
   * @return the definition expression if found, otherwise null.
   */
  public void find() {}
}

class indentHandling {
  
  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
   *I have no space before the asterisks.
   *It may seem dangerous, but it's not. 
   */
  void noSpace(){}

  <warning descr="Javadoc comment can be Markdown documentation comment">/**</warning>
                                 hello
   */
  void weloveSpace(){}
}