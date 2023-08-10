import java.lang.annotation.*;
import java.util.*;
import static java.lang.annotation.ElementType.*;

@interface AA1 { }
@Repeatable(<error descr="Invalid container annotation 'AA1': no 'value' method declared">AA1.class</error>) @interface A1 { }

@interface AA2 { String[] value(); }
@Repeatable(<error descr="Invalid container annotation 'AA2': 'value' method should have type 'A2[]'">AA2.class</error>) @interface A2 { }

@interface AA3 { A3[] value(); }
@Repeatable(<error descr="Container annotation 'AA3' has shorter retention ('CLASS') than the contained annotation">AA3.class</error>)
@Retention(RetentionPolicy.RUNTIME) @interface A3 { }

@interface A4 { }
@<error descr="Duplicate annotation. The declaration of 'A4' does not have a valid java.lang.annotation.Repeatable annotation">A4</error>
@<error descr="Duplicate annotation. The declaration of 'A4' does not have a valid java.lang.annotation.Repeatable annotation">A4</error>
class C4 { }
@A4 class C4bis { }

@<error descr="Duplicate annotation. Invalid container annotation 'AA1': no 'value' method declared">A1</error>
@<error descr="Duplicate annotation. Invalid container annotation 'AA1': no 'value' method declared">A1</error>
class C5 { }
@A1 class C5bis { }

@interface AA6 { A6[] value() default { }; }
@Repeatable(AA6.class) @interface A6 { }
@A6 @A6 <error descr="Container annotation 'AA6' must not be present at the same time as the element it contains">@AA6</error> class C6 { }
@A6 @A6 class C6bis1 { }
@A6 @AA6 class C6bis2 { }
@A6 
@<error descr="Duplicate annotation. The declaration of 'AA6' does not have a valid java.lang.annotation.Repeatable annotation">AA6</error>
@<error descr="Duplicate annotation. The declaration of 'AA6' does not have a valid java.lang.annotation.Repeatable annotation">AA6</error>
class C6bis3 { }

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

@interface AA7 { A7[] value() default { }; }
@Target({METHOD}) @Repeatable(<error descr="Target of container annotation 'AA7' is not a subset of target of this annotation">AA7.class</error>) @interface A7 { }

@Target({METHOD}) @interface AA8 { A8[] value() default { }; }
@Target({METHOD, FIELD}) @Repeatable(AA8.class) @interface A8 { }
class C8 {
  @A8 int f1;
  <error descr="Container annotation '@AA8' is not applicable to field">@A8</error> <error descr="Container annotation '@AA8' is not applicable to field">@A8</error> int f2;
  @A8 @A8 void m() { }
}

@interface AA9 { A9[] value(); int i(); }
@Repeatable(<error descr="Container annotation 'AA9' does not have a default value for 'i'">AA9.class</error>) @interface A9 { }

@Repeatable(<error descr="Invalid container annotation 'java.lang.annotation.Annotation': no 'value' method declared">Annotation.class</error>)
@interface A10 {}

@interface AA11 { A11[] value();}

@Documented
@Repeatable(<error descr="Container annotation 'AA11' does not have required @Documented annotation">AA11.class</error>)
@interface A11 {
}

class ElementTypeContainerRules {
  @Repeatable(Tags1.class)
  @Target(TYPE_USE)
  @interface Tag1 {
    String value();
  }

  @Target(TYPE)
  @interface Tags1 {
    Tag1[] value();
  }

  
  @Repeatable(Tags2.class)
  @Target(TYPE)
  @interface Tag2 {
    String value();
  }

  @Target(ANNOTATION_TYPE)
  @interface Tags2 {
    Tag2[] value();
  }
  
  @Repeatable(Tags3.class)
  @Target(TYPE_USE)
  @interface Tag3 {
    String value();
  }

  @Target(TYPE_PARAMETER)
  @interface Tags3 {
    Tag3[] value();
  }
  
  @Repeatable(Tags4.class)
  @Target(TYPE_USE)
  @interface Tag4 {
    String value();
  }

  @Target(ANNOTATION_TYPE)
  @interface Tags4 {
    Tag4[] value();
  }

}