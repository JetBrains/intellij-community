// "Add 'throws RuntimeException' to method signature|->Change only this method" "true-preview"
class a {
  int f() throws NullPointerException {
    
  }
  
  class b extends a {

    int f() {
      throw new RuntimeException()<caret>;
    }
  }
}

