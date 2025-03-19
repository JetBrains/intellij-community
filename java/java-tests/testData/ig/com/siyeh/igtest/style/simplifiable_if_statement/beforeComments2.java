// "Replace 'if else' with '?:'" "INFORMATION"
class Test {
  String foo(String currentBranch) {
    <caret>if (currentBranch.isEmpty()) {
      return currentBranch;
    }
    else {
      return currentBranch.substring(0); // comment
    }
  }
}