// "Convert to record class" "false"

// Converting this to record would create a new constructor (the record canonical constructor) with a different number of parameters
// than the already existing constructor, which may not be desirable.

class R<caret> {
  final int x;
  final int y;
  final int z;

  R(int x, int y) {
    this.x = x;
    this.y = y;
    this.z = 10;
  }
}
