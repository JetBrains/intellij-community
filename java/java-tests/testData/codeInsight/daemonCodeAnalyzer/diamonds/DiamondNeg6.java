class Neg06 {

   static class CSuperFoo<X> {}
   static class CFoo<X extends Number> extends CSuperFoo<X> {}

   CSuperFoo<String> csf1 = new <error descr="Incompatible types. Found: 'Neg06.CFoo<java.lang.String & java.lang.Number>', required: 'Neg06.CSuperFoo<java.lang.String>'">CFoo<></error>();
}
