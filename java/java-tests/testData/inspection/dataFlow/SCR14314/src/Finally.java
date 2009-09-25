import java.io.*;

public class Finally {
   public void foo(Object o) {
     try {
       if (o == null) return;
     }
     finally {
       System.out.println(o.hashCode()); // Error here.
     }
     System.out.println(o.hashCode()); // No error here.
   }

   public void bar(Object o) {
      boolean rearrangeChildren = false;
      Object typePattern;
      try {
        typePattern = parseTypePattern();
      } catch (FileNotFoundException followsFailure) {
        if (o != null) {
          typePattern = followsFailure.getParsingResult();
        } else {
          throw followsFailure;
        }

        rearrangeChildren = true;
      }

      if (rearrangeChildren) {
         System.out.println("Can be here.");
      }
   }

   Object parseTypePattern() throws IOException {
      return null;
   }
}