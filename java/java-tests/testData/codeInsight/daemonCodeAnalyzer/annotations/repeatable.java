import java.lang.annotation.*;
import java.util.*;
import static java.lang.annotation.ElementType.*;

@interface AA1 { }
@Repeatable(<error descr="Invalid containing annotation 'AA1': no 'value' method declared">AA1.class</error>) @interface A1 { }

@interface AA2 { String[] value(); }
@Repeatable(<error descr="Invalid containing annotation 'AA2': 'value' method should have type 'A2[]'">AA2.class</error>) @interface A2 { }

@interface AA3 { A3[] value(); }
@Repeatable(<error descr="Containing annotation 'AA3' has shorter retention ('CLASS') than the contained annotation">AA3.class</error>)
@Retention(RetentionPolicy.RUNTIME) @interface A3 { }

@interface A4 { }
@<error descr="Duplicate annotation. The declaration of 'A4' does not have a valid java.lang.annotation.Repeatable annotation">A4</error>
@<error descr="Duplicate annotation. The declaration of 'A4' does not have a valid java.lang.annotation.Repeatable annotation">A4</error>
class C4 { }
@A4 class C4bis { }

@<error descr="Duplicate annotation. Invalid containing annotation 'AA1': no 'value' method declared">A1</error>
@<error descr="Duplicate annotation. Invalid containing annotation 'AA1': no 'value' method declared">A1</error>
class C5 { }
@A1 class C5bis { }

@interface AA6 { A6[] value() default { }; }
@Repeatable(AA6.class) @interface A6 { }
@A6 @A6 <error descr="Containing annotation 'AA6' must not be present at the same time as the element it contains">@AA6</error> class C6 { }
@A6 @A6 class C6bis1 { }
@A6 @AA6 class C6bis2 { }

@Target({TYPE_USE}) @interface TA { }
class DupTypeAnno {
  List<@<error descr="Duplicate annotation. The declaration of 'TA' does not have a valid java.lang.annotation.Repeatable annotation">TA</error> @<error descr="Duplicate annotation. The declaration of 'TA' does not have a valid java.lang.annotation.Repeatable annotation">TA</error> String> l = null;
  Boolean[] b = new Boolean @<error descr="Duplicate annotation. The declaration of 'TA' does not have a valid java.lang.annotation.Repeatable annotation">TA</error> @<error descr="Duplicate annotation. The declaration of 'TA' does not have a valid java.lang.annotation.Repeatable annotation">TA</error> [42];

  {
    this.<@TA String @<error descr="Duplicate annotation. The declaration of 'TA' does not have a valid java.lang.annotation.Repeatable annotation">TA</error> @<error descr="Duplicate annotation. The declaration of 'TA' does not have a valid java.lang.annotation.Repeatable annotation">TA</error> []>m();
    this.<@<error descr="Duplicate annotation. The declaration of 'TA' does not have a valid java.lang.annotation.Repeatable annotation">TA</error> @<error descr="Duplicate annotation. The declaration of 'TA' does not have a valid java.lang.annotation.Repeatable annotation">TA</error> String @TA []>m();
    this.<@TA String @TA [] @TA []>m();
  }

  static <T> void m() { }
}
