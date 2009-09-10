class <caret>C<X,Y> {
}

class Usage extends C<String,Integer> {
  {
    C<Boolean,String> = new C();
  }
}