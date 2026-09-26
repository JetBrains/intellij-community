// "Convert to record class" "false"

// Converting this to record would create a new constructor (the record canonical constructor) with a different number of parameters
// than the already existing constructor, which may not be desirable.

class <caret>R {
  final int first;

  R(int first, int second) {
    this.first = first;
  }
}
