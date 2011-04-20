class Neg08 {
   public static void main(String[] args) {
     String s = new String<<error descr="Diamond operator is not applicable for non-parameterized types"></error>>("foo");
   }
}
