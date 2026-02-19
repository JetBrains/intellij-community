class NotInBlock {

  public void test(boolean b) {
      //before update
      if(b) //end line comment
      {
          int i=0;//before condition
          while (i</*comment in condition*/10) {
            //before statement
            System.out.println("Hello!");//in body
              i/*comment inside update*/++;
          }
      }
  }
}