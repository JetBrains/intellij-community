// "Convert to record class" "true-preview"
class <caret>R {
  final int myFirst;

  R(int first) {
    myFirst = first;
    System.out.println("hello there, myFirst: " + first + ", first: " + first);
    // Renaming usages in strings can be enabled with 'RenameRefactoring.setSearchInComments(false)', but it always prompts, so it's a no-go
  }

  // first
  // myFirst
}
