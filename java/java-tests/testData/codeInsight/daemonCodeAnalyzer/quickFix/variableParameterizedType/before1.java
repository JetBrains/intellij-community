// "Change variable 'i' type to 'a.i.ii<String>'" "true"
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
      i2.ii<<caret>String> i;
   }
}
