public class <warning descr="Class 'XXX' is never used">XXX</warning> {
  void <warning descr="Method 'fffff()' is never used">fffff</warning>(){}
  public void <warning descr="Method 'asdfaffffff()' is never used">asdfaffffff</warning>(){}
  protected void <warning descr="Method 'dasklfjhsad()' is never used">dasklfjhsad</warning>(){}

  String <warning descr="Field 'asdfadsf' is never used">asdfadsf</warning>;
  public String <warning descr="Field 'asdasdfadsf' is never used">asdasdfadsf</warning>;
  static class <warning descr="Class 'sasdfasdfasd' is never used">sasdfasdfasd</warning> {}
  
  public <warning descr="Constructor 'XXX()' is never used">XXX</warning>() {}


  // overloaded
  void fffff(String i){i.hashCode();}
  {
     fffff(null);
  }
}

