class NoStaticInInnerClass {

  {
    new Inner();
  }
  
  class Inner {
    {
      System.out.println("Sunshine and happiness");
    }
  }
}