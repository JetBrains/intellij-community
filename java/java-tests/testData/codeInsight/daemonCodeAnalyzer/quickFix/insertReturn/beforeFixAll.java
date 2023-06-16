// "Apply all 'Insert 'return'' fixes in file" "true"
class Test {
  int x(int y) {
    Math.abs(y)<caret>
  }

  int z(int y) {
    Math.abs(y)
  }
}