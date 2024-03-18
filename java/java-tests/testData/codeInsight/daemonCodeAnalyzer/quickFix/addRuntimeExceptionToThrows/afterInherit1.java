// "Add 'throws RuntimeException' to method signature" "true-preview"
class a {
  int f() throws RuntimeException {
    
  }
  
  class b extends a {

    int f() throws RuntimeException {
      throw new RuntimeException();
    }
  }
}

