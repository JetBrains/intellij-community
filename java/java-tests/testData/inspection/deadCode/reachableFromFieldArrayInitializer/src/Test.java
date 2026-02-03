public class Test implements Interface{
   public static int[] myField = new int[] {init()};

   public static int init() {
     return 1;
   }
   
   public static void main(String[] args){
     Interface i = new Test();
     System.out.println(i.getField());
   }

   public int[] getField() {
     return myField;
   }
}