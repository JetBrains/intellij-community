class X {
  void f(int[] a){
    for(int i=0; i<a.length; i++)  {
      System.out.println(a[i]);<caret>
    }
  }
}