abstract class StraightLine implements Inline {
}

interface Inline {

  void g<caret>o(); // inline this method
}