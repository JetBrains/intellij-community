class Neg06 {

   static class CSuperFoo<X> {}
   static class CFoo<X extends Number> extends CSuperFoo<X> {}

   <error descr="Incompatible types. Found: 'Neg06.CFoo<java.lang.String>', required: 'Neg06.CSuperFoo<java.lang.String>'">CSuperFoo<String> csf1 = new CFoo<>();</error>
}
