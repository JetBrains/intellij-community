import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Hashtable;
class TypeUseWithTypeHighlighting {

  @ReadOnly
  private String field;

  @ReadOnly private String fieldOneLine;

  private <warning descr="Missorted modifiers '@ReadOnly final'">@ReadOnly</warning> <error descr="Field 'field1' might not have been initialized">final String field1</error>;
  private final @ReadOnly String field2 = "2";
}
@Target({ElementType.TYPE_USE, ElementType.FIELD})
@interface ReadOnly {}
