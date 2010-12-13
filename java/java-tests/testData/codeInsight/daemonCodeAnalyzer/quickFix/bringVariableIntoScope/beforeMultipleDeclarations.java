// "Bring 'int j' into Scope" "true"
class a {
    void foo() {
    {
      int i, j = 10;
    }
    System.out.println(<caret>j); // invoke "bring 'int j' into scope" quickfix here
  }
}