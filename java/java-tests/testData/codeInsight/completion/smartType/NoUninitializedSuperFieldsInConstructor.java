abstract class SuggestBase {
  final int baseField;
  static final int baseConstant;

  SuggestBase(int baseField) {
    this.baseField = baseField;
  }
}

class SuggestChild extends SuggestBase {
  SuggestChild(int input) {
    super(<caret>)
  }
}