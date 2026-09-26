// "Convert to record class" "true-preview"

class Poi<caret>nt {
    final double x;
    final double y;

    Point(double x) {
      this.x = x;
      this.y = 0;
    }

    // During conversion to record, this constructor will become a canonical constructor, and then be removed because it's redundant.
    // Removal of a redundant canonical constructor is actually an inspection on its own: RedundantRecordConstructorInspection.
    Point(double x, double y) {
      this.x = x;
      this.y = y;
    }
}
