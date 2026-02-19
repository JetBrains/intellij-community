// "Convert to record class" "true-preview"

/// JLS 8.10.1 says:
///
/// A record component may be a variable arity record component, indicated by an ellipsis following the type.
/// At most one variable arity record component is permitted for a record class.
/// It is a compile-time error if a variable arity record component appears anywhere in the list of record components except the last position.
final class R<caret> {
    final int single;
    final int[] array;

    private R(int single, int... array) {
        this.single = single;
        this.array = array;
    }
}
