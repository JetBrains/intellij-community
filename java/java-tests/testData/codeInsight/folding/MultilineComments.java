<fold text='/.../'>/*
file header comment
 */</fold>

package test;
  <fold text='/*...*/'>/*
outer class comment
 */</fold>

<fold text='//...'>//outer class
//consequent EOL comments</fold>

import java.util.List;
<fold text='/*...*/'>/*
outer class comment
 */</fold>

<fold text='//...'>//outer class
//consequent EOL comments</fold>

<fold text='/*...*/'>/*
before class comment
 */</fold>
class Test {
  <fold text='/*...*/'>/*
  dangling multiline commnent
   */</fold>

  <fold text='/*...*/'>/*
  field comment
   */</fold>
  int field;

  <fold text='/*...*/'>/*
  method comment
  */</fold>
  void foo() <fold text='{...}'>{

    <fold text='/*...*/'>/*
    method var comment
     */</fold>
    final int i = 42;
  }</fold>

  <fold text='/*...*/'>/*
  inner class comment
   */</fold>
  class Inner <fold text='{...}'>{

    <fold text='/*...*/'>/*
    inner class method comment
     */</fold>
    void foo() <fold text='{...}'>{
      class MethodInner <fold text='{...}'>{
        <fold text='/*...*/'>/*
        method inside class declared in method comment
        */</fold>
        void foo() <fold text='{}'>{

        }</fold>
      }</fold>
    }</fold>
  }</fold>
}
<fold text='/*...*/'>/*
after class comment
 */</fold>
<fold text='//...'>// after class
//consequent EOL comments</fold>