// "Change variable 'i' type to 'a.i.ii<java.lang.String>'" "true"
class a
{
  class i {
    class ii<E> {

    }
  }
  class i2 {
    class ii {

    }
  }

   public void foo()
   {
      i.ii<<caret>String> i;
   }
}
