final class Junk
   {
   public void sillyMethod()
      {
      String <warning descr="Variable 'bar' can have 'final' modifier">bar</warning> = "bar";
      try
         {
         int <warning descr="Variable 'i' can have 'final' modifier">i</warning> = 1;
         String <warning descr="Variable 'foo' can have 'final' modifier">foo</warning> = "foo";
         System.out.println("[" + bar + "|" + i + "|" + foo + "]");
         throw new Exception();
         }
      catch (Exception <warning descr="Parameter 'e' can have 'final' modifier">e</warning>)
         {
         int <warning descr="Variable 'j' can have 'final' modifier">j</warning> = 2;
         System.out.println("j = [" + j + "]");
         }
      finally
         {
         System.out.println("In finally");
         }
      }
   }

