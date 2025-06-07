// "Convert to record class" "false"
class R2<caret> {
  final int myFirst;
  final int mySecond;
  final int myThird;

  R2(int first, int nothing, int third) {
    myFirst = first;
    mySecond = nothing;
    myThird = idk; // 2 problems here:
                   //  1. unresolved symbol 'idk'
                   //  2. no connection between 'myThird' field and 'third' parameter
                   // So we can't do conversion reliably.
  }
}
