import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Hashtable;
class TypeUseWithTypeHighlighting {

  <warning descr="Missorted modifiers '@ReadOnly private'">@ReadOnly</warning>
  private String field;

  <warning descr="Missorted modifiers '@ReadOnly private'">@ReadOnly</warning> private String fieldOneLine;

  private <warning descr="Missorted modifiers '@ReadOnly final'">@ReadOnly</warning> <error descr="Field 'field1' might not have been initialized">final String field1</error>;
  private final @ReadOnly String field2 = "2";
}
@Target({ElementType.TYPE_USE, ElementType.FIELD})
@interface ReadOnly {}
