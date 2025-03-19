class MarkdownJepExample {

  /// Returns a hash code value for the object. This method is
  /// supported for the benefit of hash tables such as those provided by
  /// [java.util.HashMap].
  ///
  /// The general contract of `hashCode` is:
  ///
  ///   - Whenever it is invoked on the same object more than once during
  ///     an execution of a Java application, the `hashCode` method
  ///     must consistently return the same integer, provided no information
  ///     used in `equals` comparisons on the object is modified.
  ///     This integer need not remain consistent from one execution of an
  ///     application to another execution of the same application.
  ///   - If two objects are equal according to the
  ///     [equals][#equals(Object)] method, then calling the
  ///     `hashCode` method on each of the two objects must produce the
  ///     same integer result.
  ///   - It is _not_ required that if two objects are unequal
  ///     according to the [equals][#equals(Object)] method, then
  ///     calling the `hashCode` method on each of the two objects
  ///     must produce distinct integer results.  However, the programmer
  ///     should be aware that producing distinct integer results for
  ///     unequal objects may improve the performance of hash tables.
  ///
  /// @implSpec
  /// As far as is reasonably practical, the `hashCode` method defined
  /// by class `Object` returns distinct integers for distinct objects.
  ///
  /// @return  a hash code value for this object.
  /// @see     java.lang.Object#equals(java.lang.Object)
  /// @see     java.lang.System#identityHashCode
  public String longExample() { return null; }
}