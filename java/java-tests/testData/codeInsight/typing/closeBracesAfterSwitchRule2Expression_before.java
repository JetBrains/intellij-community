class X {
  public static void main(String[] args) {
    switch (new Object()){
      case Object object -> <caret>
           System.out.println("1");
           System.out.println("1");
    }
  }
}