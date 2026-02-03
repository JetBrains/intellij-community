public class ExtractMethods {
   void newMethod() {
     int i = 0;
       newMethod(i);
   }

    private void newMethod(int i) {
        if (true) {
          newMethod(i);
        }
    }
}
