// "Change variable 'i' type to 'a.i.ii<String>'" "true-preview"
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
