class Neg06 {
   interface ISuperFoo<X> {}
   interface IFoo<X extends Number> extends ISuperFoo<X> {}

   static class CSuperFoo<X> {}
   static class CFoo<X extends Number> extends CSuperFoo<X> {}

   ISuperFoo<String> isf = new IFoo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>() {};
   CSuperFoo<String> csf1 = new CFoo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>();
   CSuperFoo<String> csf2 = new CFoo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>() {};
}
