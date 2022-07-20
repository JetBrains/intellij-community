// "Bring 'int j' into scope" "true-preview"
class a {
    void foo() {
    {
      int i, j = 10;
    }
    System.out.println(<caret>j);// invoke "bring 'int j' into scope" quickfix here
  }
}