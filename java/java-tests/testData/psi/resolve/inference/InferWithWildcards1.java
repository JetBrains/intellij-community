class C<T> {
   <T> T foo (C<? extends T> c) {return null;}

   void bar (C<? extends String> c) {
     <caret>foo(c);
   }
}