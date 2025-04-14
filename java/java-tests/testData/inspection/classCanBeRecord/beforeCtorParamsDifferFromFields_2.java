// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
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
