class Neg06 {

   static class CSuperFoo<X> {}
   static class CFoo<X extends Number> extends CSuperFoo<X> {}

   CSuperFoo<String> csf1 = new CFoo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>();
}
